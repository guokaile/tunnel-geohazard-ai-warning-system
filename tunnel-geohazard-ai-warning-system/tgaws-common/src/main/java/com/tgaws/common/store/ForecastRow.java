package com.tgaws.common.store;

import java.math.BigDecimal;

/**
 * 预测结果行（ai_forecast 行映射）。
 *
 * @param pointId       点位 id
 * @param modelId       模型 id（0=内置统计模型未注册占位）
 * @param forecastAtMs  批算时刻（Unix 毫秒）
 * @param targetTimeMs  预测目标时刻（Unix 毫秒）
 * @param forecastValue 预测值
 * @param lowerBound    置信区间下界（可空）
 * @param upperBound    置信区间上界（可空）
 */
public record ForecastRow(
        long pointId,
        long modelId,
        long forecastAtMs,
        long targetTimeMs,
        BigDecimal forecastValue,
        BigDecimal lowerBound,
        BigDecimal upperBound
) {
}
