package com.tgaws.common.store;

import java.math.BigDecimal;

/**
 * 采样行（存储契约跨模块数据对象）。
 *
 * @param pointId 点位 id（由 {@link IPointResolver} 按点位编码解析）
 * @param tsMs    采样时间（Unix 毫秒，与 data_sample.ts datetime(3) 对齐）
 * @param value   监测值（decimal(16,4) 语义）
 * @param quality 质量位（0正常 1超范围 2跳变 3缺失）
 * @param source  来源（1TCP 2MQTT 3人工 4导入 5第三方）
 */
public record SampleRow(
        long pointId,
        long tsMs,
        BigDecimal value,
        int quality,
        int source
) {
}
