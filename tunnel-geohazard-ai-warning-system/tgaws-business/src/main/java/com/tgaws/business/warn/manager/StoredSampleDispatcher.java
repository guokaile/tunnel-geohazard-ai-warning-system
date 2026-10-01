package com.tgaws.business.warn.manager;

import com.tgaws.common.store.ISampleStoredSink;
import com.tgaws.common.store.SampleRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 入库成功回调实现（评审 3.2：事务提交后驱动快速通道，非轮询）：
 * SampleBatchWriter 双触发（500 条|1s）成功后同步回调 → 发布 Spring 事件 →
 * WarnJudgeService 监听做判定编排。链路延迟：队列驻留 ≤1s + 写库 <100ms + 判定 <1ms。
 */
@Component
public class StoredSampleDispatcher implements ISampleStoredSink {

    private static final Logger log = LoggerFactory.getLogger(StoredSampleDispatcher.class);

    private final ApplicationEventPublisher eventPublisher;

    public StoredSampleDispatcher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void onStored(List<SampleRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        log.debug("入库成功回调：{} 行，发布判定事件", rows.size());
        eventPublisher.publishEvent(new SamplesStoredEvent(this, rows));
    }

    /** 入库成功事件（监听者：WarnJudgeService） */
    public record SamplesStoredEvent(Object source, List<SampleRow> rows) {
    }
}
