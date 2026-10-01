package com.tgaws.compute.pipeline;

import com.tgaws.common.pipeline.DataFrame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据管道单测（《6》6.3.1：双触发/折半重试/背压两级/未解析隔离）。
 *
 * <p>背压路径用调小环容量 + 慢消费者/阻塞消费者构造——T2a 长跑（33 条/s）永远走不到这里，
 * 必须单测直接触发（评审要求）。</p>
 */
class DisruptorPipelineTest {

    private static final String GW = "GW001";
    private static final String POINT = "P00010001";

    private StubStore store;
    private DisruptorPipeline pipeline;

    @BeforeEach
    void setUp() {
        store = new StubStore();
        // 解析映射：POINT→1（正常行）、P00020001→999（坏行注入用）
        // 注意：禁止 ? 1L : (… : null) 三元式——基本类型分支导致 null 自动拆箱 NPE
        pipeline = new DisruptorPipeline(1024, store, code -> {
            if (POINT.equals(code)) {
                return 1L;
            }
            if ("P00020001".equals(code)) {
                return 999L;
            }
            return null;
        });
        pipeline.start();
    }

    @AfterEach
    void tearDown() {
        pipeline.stop();
    }

    @Test
    void dualTriggerBySize() throws Exception {
        for (int i = 0; i < 500; i++) {
            publishOne();
        }
        await(() -> store.storedRows() >= 500, "满 500 条应立即触发批量提交");
    }

    @Test
    void dualTriggerByTime() throws Exception {
        publishOne();
        publishOne();
        publishOne();
        await(() -> store.storedRows() >= 3, "满 1s 时间触发（无新事件时由定时兜底器触发）");
    }

    @Test
    void halvingRetryIsolatesBadRow() throws Exception {
        store.badPointIds = Set.of(999L);
        for (int i = 0; i < 400; i++) {
            publishOne(); // pointId=1 正常行
        }
        pipeline.publish(frame("P00020001")); // 坏行（resolver 映射 pointId=999）
        await(() -> pipeline.writtenRows() == 400 && pipeline.failedRows() == 1,
                "折半递归应隔离坏行：400 好行全部写入，坏行最终失败计数 1");
        assertEquals(400, store.storedRows());
    }

    @Test
    void backpressureDropAtEighty() {
        // 小环 + 慢消费者：灌 5000 条必然触发 80% 级丢弃
        StubStore slow = new StubStore();
        slow.insertDelayMs = 2;
        DisruptorPipeline small = new DisruptorPipeline(64, slow, code -> 1L);
        small.start();
        try {
            for (int i = 0; i < 5000; i++) {
                small.publish(frame(POINT));
            }
            assertTrue(small.droppedAtEightyCount() > 0, "80% 级丢弃计数应被触发");
        } finally {
            small.stop();
        }
    }

    @Test
    void pauseAtNinetyFive() {
        // 阻塞消费者填满小环 → ≥95% 暂停接入；闩锁释放后消费者快速退出（防 shutdown 死等）
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        StubStore blocked = new StubStore() {
            @Override
            public int batchInsert(java.util.List<com.tgaws.common.store.SampleRow> rows) {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                // 释放后直接失败，消费者经折半重试快速耗尽队列并退出
                throw new RuntimeException("released");
            }
        };
        DisruptorPipeline small = new DisruptorPipeline(64, blocked, code -> 1L);
        small.start();
        try {
            long deadline = System.currentTimeMillis() + 8000;
            while (!small.isPaused() && System.currentTimeMillis() < deadline) {
                small.publish(frame(POINT));
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            assertTrue(small.isPaused(), "≥95% 应进入暂停接入状态");
            assertTrue(small.droppedAtNinetyFiveCount() > 0, "95% 级丢弃计数应被触发");
        } finally {
            release.countDown();
            small.stop();
        }
    }

    @Test
    void unresolvedPointNotInserted() throws Exception {
        pipeline.publish(frame("P-UNKNOWN"));
        await(() -> pipeline.unresolvedCount() == 1, "未解析点位应计数隔离");
        assertEquals(0, store.storedRows());
    }

    // ---------- 辅助 ----------

    private void publishOne() {
        pipeline.publish(frame(POINT));
    }

    private static DataFrame frame(String pointCode) {
        return new DataFrame(GW, pointCode, System.currentTimeMillis(),
                BigDecimal.valueOf(12.3456), 0, 1);
    }

    private static void await(Supplier<Boolean> condition, String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.get()) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(20);
        }
        throw new AssertionError(message);
    }
}
