package com.tgaws.common.pipeline;

/**
 * 数据管道发布契约（《4.接口设计说明书》4.3）。
 *
 * <p>接入层（tgaws-access）经本接口发布统一数据帧；
 * 实现（Disruptor 有界队列）位于计算层（tgaws-compute），
 * 由 web 层装配注入——接入层不依赖实现，保持可替换。</p>
 */
public interface IDataPipeline {

    /** 发布统一数据帧（有界队列：背压降级策略内聚于实现） */
    void publish(DataFrame frame);
}
