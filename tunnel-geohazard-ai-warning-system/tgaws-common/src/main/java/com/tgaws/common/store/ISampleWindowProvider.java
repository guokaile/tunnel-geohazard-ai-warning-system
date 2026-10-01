package com.tgaws.common.store;

import com.tgaws.common.rule.PointSample;

import java.util.List;
import java.util.Map;

/**
 * 采样窗口提供契约（预测批算/统计通道输入；实现位于 business，读 data_sample_minute）。
 */
public interface ISampleWindowProvider {

    /**
     * 获取全部启用点位的分钟级窗口样本（时间升序）。
     *
     * @param windowMinutes 窗口跨度（分钟）
     * @return pointId → 分钟级样本
     */
    Map<Long, List<PointSample>> minuteWindowForAllPoints(int windowMinutes);
}
