package com.tgaws.compute.pipeline;

import com.lmax.disruptor.EventHandler;
import com.tgaws.common.store.IPointLatestStore;
import com.tgaws.common.store.IPointResolver;
import com.tgaws.common.store.ISampleStore;
import com.tgaws.common.store.SampleRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 批量写消费者（《6.详细设计说明书》6.3.1：双触发 满 500 条或满 1s 先到者提交；
 * 失败整批重试 1 次 → 对半拆分递归重试 ≤log₂500≈9 层，定位坏行且仅多 9 个事务）。
 *
 * <p>依赖注入方式：ISampleStore 与 IPointLatestStore 可能为同一实现（业务层适配），
 * 由构造参数显式提供。</p>
 */
public class SampleBatchWriter implements EventHandler<SampleEvent> {

    private static final Logger log = LoggerFactory.getLogger(SampleBatchWriter.class);

    /** 双触发：批量条数 */
    public static final int BATCH_SIZE = 500;

    /** 双触发：时间间隔（毫秒） */
    public static final long FLUSH_INTERVAL_MS = 1000L;

    private final ISampleStore store;
    private final IPointLatestStore latestStore;
    private final IPointResolver resolver;

    private final List<SampleRow> pending = new ArrayList<>(BATCH_SIZE);
    private final AtomicLong lastFlushMs = new AtomicLong(System.currentTimeMillis());

    private final AtomicLong writtenRows = new AtomicLong();
    private final AtomicLong unresolvedCount = new AtomicLong();
    private final AtomicLong failedRows = new AtomicLong();

    private volatile ScheduledExecutorService scheduler;

    public SampleBatchWriter(ISampleStore store, IPointResolver resolver) {
        this(store, null, resolver);
    }

    public SampleBatchWriter(ISampleStore store, IPointLatestStore latestStore, IPointResolver resolver) {
        this.store = store;
        this.latestStore = latestStore;
        this.resolver = resolver;
    }

    @Override
    public void onEvent(SampleEvent event, long sequence, boolean endOfBatch) {
        Long pointId = resolver.resolvePointId(event.pointCode());
        if (pointId == null) {
            unresolvedCount.incrementAndGet();
            return;
        }
        pending.add(new SampleRow(pointId, event.tsMs(), event.value(), event.quality(), event.source()));
        if (pending.size() >= BATCH_SIZE) {
            flush();
        } else if (endOfBatch && System.currentTimeMillis() - lastFlushMs.get() >= FLUSH_INTERVAL_MS) {
            flush();
        }
    }

    /** 定时器兜底触发（1s 一次；无新事件时也保证时间触发） */
    public void flushIfDue() {
        if (!pending.isEmpty() && System.currentTimeMillis() - lastFlushMs.get() >= FLUSH_INTERVAL_MS) {
            flush();
        }
    }

    /** 双触发提交（flush 串行化：disruptor 线程与调度线程可能并发进入） */
    public synchronized void flush() {
        if (pending.isEmpty()) {
            return;
        }
        List<SampleRow> batch = new ArrayList<>(pending);
        pending.clear();
        lastFlushMs.set(System.currentTimeMillis());
        insertWithRetry(batch);
    }

    /** 整批重试 1 次 → 折半递归（定位坏行，最多 log₂N 层） */
    private void insertWithRetry(List<SampleRow> rows) {
        if (tryInsert(rows)) {
            return;
        }
        if (tryInsert(rows)) {
            return;
        }
        if (rows.size() == 1) {
            failedRows.incrementAndGet();
            log.error("采样行写入最终失败：pointId={} ts={}", rows.get(0).pointId(), rows.get(0).tsMs());
            return;
        }
        int mid = rows.size() / 2;
        insertWithRetry(new ArrayList<>(rows.subList(0, mid)));
        insertWithRetry(new ArrayList<>(rows.subList(mid, rows.size())));
    }

    private boolean tryInsert(List<SampleRow> rows) {
        try {
            int n = store.batchInsert(rows);
            if (n == rows.size()) {
                if (latestStore != null) {
                    rows.forEach(latestStore::upsert);
                }
                writtenRows.addAndGet(rows.size());
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("批量写失败（{} 行），进入重试/折半路径：{}", rows.size(), e.getMessage());
            return false;
        }
    }

    /** 启动 1s 定时兜底触发器 */
    public void startScheduler() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sample-flush-timer");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::flushIfDue, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /** 停止定时器（关停顺序：先停触发器再 drain） */
    public void stopScheduler() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    public long writtenRows() {
        return writtenRows.get();
    }

    public long unresolvedCount() {
        return unresolvedCount.get();
    }

    public long failedRows() {
        return failedRows.get();
    }
}
