package com.tgaws.compute.pipeline;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.tgaws.common.pipeline.DataFrame;
import com.tgaws.common.pipeline.IDataPipeline;
import com.tgaws.common.store.IPointLatestStore;
import com.tgaws.common.store.IPointResolver;
import com.tgaws.common.store.ISampleStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Disruptor 有界数据管道（《6.详细设计说明书》6.3.1，IDataPipeline 实现）。
 *
 * <p>背压两级：①≥80% 级=容量预警计数（不丢数据，自监控告警联动）；
 * ②环满（tryNext 失败，等价 ≥95%）=丢弃新入+计数+暂停接入信号（示意网关降速）。
 * 丢弃仅允许发生在超设计峰值场景（持续 >300 条/s 超 5min），日完整率 ≥99.99% 口径。</p>
 */
public class DisruptorPipeline implements IDataPipeline {

    private static final Logger log = LoggerFactory.getLogger(DisruptorPipeline.class);

    /** 默认环容量（2^20，约 100 万条） */
    public static final int DEFAULT_RING_SIZE = 1 << 20;

    private final int bufferSize;
    private final SampleBatchWriter writer;
    private final Disruptor<SampleEvent> disruptor;

    private volatile RingBuffer<SampleEvent> ringBuffer;

    private final AtomicLong droppedAtEighty = new AtomicLong();
    private final AtomicLong droppedAtNinetyFive = new AtomicLong();
    private final AtomicBoolean paused = new AtomicBoolean(false);

    public DisruptorPipeline(ISampleStore store, IPointResolver resolver) {
        this(DEFAULT_RING_SIZE, store, null, resolver);
    }

    public DisruptorPipeline(int ringSize, ISampleStore store, IPointResolver resolver) {
        this(ringSize, store, null, resolver);
    }

    public DisruptorPipeline(int ringSize, ISampleStore store, IPointLatestStore latestStore,
                             IPointResolver resolver) {
        this.bufferSize = ringSize;
        this.writer = new SampleBatchWriter(store, latestStore, resolver);
        this.disruptor = new Disruptor<>(
                SampleEvent::new, bufferSize,
                DaemonThreadFactory.INSTANCE, ProducerType.MULTI, new BlockingWaitStrategy());
        this.disruptor.handleEventsWith(writer);
    }

    /** 启动（返回后即可发布） */
    public void start() {
        ringBuffer = disruptor.start();
        writer.startScheduler();
        log.info("数据管道已启动：环容量 {}（双触发 {}条|{}ms）",
                bufferSize, SampleBatchWriter.BATCH_SIZE, SampleBatchWriter.FLUSH_INTERVAL_MS);
    }

    @Override
    public void publish(DataFrame frame) {
        RingBuffer<SampleEvent> rb = ringBuffer;
        if (rb == null) {
            throw new IllegalStateException("管道未启动");
        }
        long seq;
        try {
            // 先尝试入环：环满（tryNext 失败）才是真正的容量耗尽
            seq = rb.tryNext(1);
        } catch (InsufficientCapacityException e) {
            droppedAtNinetyFive.incrementAndGet();
            paused.set(true);
            return;
        }
        SampleEvent event = rb.get(seq);
        event.set(frame.gatewayCode(), frame.pointCode(), frame.ts(),
                frame.value(), frame.quality(), frame.source());
        rb.publish(seq);
        // 80% 级：容量预警计数（不丢数据，仅告警；95% 级由环满触发丢+暂停）
        long used = bufferSize - rb.remainingCapacity();
        if ((double) used / bufferSize >= 0.80D) {
            droppedAtEighty.incrementAndGet();
        }
    }

    /** 优雅关停：停调度器 → drain 消费 → 停机 */
    public void stop() {
        writer.stopScheduler();
        disruptor.shutdown();
        log.info("数据管道已停止（写入 {} / 丢弃 80%级 {} / 丢弃 95%级 {} / 未解析 {} / 失败 {}）",
                writer.writtenRows(), droppedAtEighty.get(), droppedAtNinetyFive.get(),
                writer.unresolvedCount(), writer.failedRows());
    }

    /** 是否已进入暂停接入状态（≥95% 触发） */
    public boolean isPaused() {
        return paused.get();
    }

    /** 80% 级丢弃计数（自监控告警联动） */
    public long droppedAtEightyCount() {
        return droppedAtEighty.get();
    }

    /** 95% 级丢弃计数 */
    public long droppedAtNinetyFiveCount() {
        return droppedAtNinetyFive.get();
    }

    /** 写入成功行数 */
    public long writtenRows() {
        return writer.writtenRows();
    }

    /** 未解析点位计数（B0101 语义） */
    public long unresolvedCount() {
        return writer.unresolvedCount();
    }

    /** 最终失败行数（折半重试后仍失败，D0001 告警） */
    public long failedRows() {
        return writer.failedRows();
    }
}
