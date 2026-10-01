package com.tgaws.common.store;

import java.util.List;

/**
 * 入库成功回调契约（快速通道事件驱动链路，评审 3.2）：
 *
 * <p>SampleBatchWriter 在事务提交成功（tryInsert 全绿）后同步调用 onStored；
 * 实现位于 business（发布 Spring ApplicationEvent 触发判定编排）。
 * 链路延迟预算：队列驻留 ≤1s（双触发 500 条|1s）+ 写库 <100ms + 判定 <1ms。</p>
 */
public interface ISampleStoredSink {

    /** 批量入库成功回调（与写库同线程，随后事件广播异步判定） */
    void onStored(List<SampleRow> rows);
}
