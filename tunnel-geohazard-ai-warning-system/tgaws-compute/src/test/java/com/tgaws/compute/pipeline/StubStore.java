package com.tgaws.compute.pipeline;

import com.tgaws.common.store.IPointLatestStore;
import com.tgaws.common.store.ISampleStore;
import com.tgaws.common.store.SampleRow;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存桩存储（管道单测）：可注入整批失败次数与"坏行"集合，验证折半重试与背压。
 */
class StubStore implements ISampleStore, IPointLatestStore {

    final ConcurrentLinkedQueue<List<SampleRow>> batches = new ConcurrentLinkedQueue<>();
    final AtomicLong totalRows = new AtomicLong();
    final AtomicLong latestUpserts = new AtomicLong();

    /** 整批失败注入：前 N 次 batchInsert 抛异常 */
    final AtomicInteger failNextCalls = new AtomicInteger();

    /** 坏行注入：包含这些 pointId 的批次抛异常（模拟数据库拒绝） */
    volatile Set<Long> badPointIds = Set.of();

    /** 每批插入延迟（背压测试用慢消费者） */
    volatile long insertDelayMs = 0;

    @Override
    public int batchInsert(List<SampleRow> rows) {
        if (insertDelayMs > 0) {
            try {
                Thread.sleep(insertDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        if (failNextCalls.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
            throw new RuntimeException("注入整批失败");
        }
        for (SampleRow row : rows) {
            if (badPointIds.contains(row.pointId())) {
                throw new RuntimeException("注入坏行 pointId=" + row.pointId());
            }
        }
        batches.add(List.copyOf(rows));
        totalRows.addAndGet(rows.size());
        return rows.size();
    }

    @Override
    public void upsert(SampleRow row) {
        latestUpserts.incrementAndGet();
    }

    long storedRows() {
        return totalRows.get();
    }
}
