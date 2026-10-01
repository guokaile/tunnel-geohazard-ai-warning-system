package com.tgaws.common.pipeline;

import java.math.BigDecimal;

/**
 * 统一数据帧（接入层产出、计算/业务层消费的跨模块契约，对应《4》4.3 IDataPipeline）。
 *
 * @param gatewayCode 网关编号（GW_ID）
 * @param pointCode   点位编码（ASCII，与 mon_point.point_code 匹配）
 * @param ts          采样时间（Unix 毫秒）
 * @param value       监测值（由协议缩放整数按 scale 换算，精确 decimal 语义）
 * @param quality     质量位（0正常 1超范围 2跳变 3缺失）
 * @param source      来源（1TCP 2MQTT 3人工 4导入 5第三方）
 */
public record DataFrame(
        String gatewayCode,
        String pointCode,
        long ts,
        BigDecimal value,
        int quality,
        int source
) {
}
