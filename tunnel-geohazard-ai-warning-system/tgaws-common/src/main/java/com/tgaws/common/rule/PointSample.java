package com.tgaws.common.rule;

import java.math.BigDecimal;

/**
 * 点位采样（规则/检测/预测共用的跨层值对象，契约层位于 common）。
 *
 * @param tsMs    采样时间（Unix 毫秒）
 * @param value   监测值
 * @param quality 质量位（研判降权系数见 QualityFlag.judgeWeight）
 */
public record PointSample(long tsMs, BigDecimal value, int quality) {
}
