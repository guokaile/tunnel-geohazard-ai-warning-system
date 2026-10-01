package com.tgaws.access.tcp.protocol;

import java.util.List;

/**
 * TCP 采集协议帧（解析结果，对应《4》4.4.1 帧结构）。
 *
 * @param type      帧类型
 * @param gatewayId 网关编号（ASCII，右补 0x00）
 * @param tsMs      Unix 毫秒时间戳
 * @param items     数据块列表（心跳帧为空）
 */
public record TcpFrame(
        FrameType type,
        String gatewayId,
        long tsMs,
        List<FrameItem> items
) {

    /**
     * 数据块：点位编码（ASCII，≤32 字节 [A-Z0-9-]）+ 质量位 + 值（int64 缩放整数）。
     *
     * @param pointCode 点位编码
     * @param quality   质量位（0~3）
     * @param valueRaw  缩放整数原始值（实际值 = valueRaw × 10^-scale）
     */
    public record FrameItem(
            String pointCode,
            int quality,
            long valueRaw
    ) {
    }
}
