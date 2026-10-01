package com.tgaws.business.mon.vo;

import java.time.LocalDateTime;

/**
 * 网关台账视图对象（评审 4.2：类型层隔离——**本 VO 不存在 secret 字段**，
 * 密文绝不外泄；连脱敏回显都不提供）。
 */
public record GatewayVo(
        Long id,
        String gatewayCode,
        String gatewayName,
        Integer protocol,
        Integer scale,
        Integer status,
        Integer onlineStatus,
        LocalDateTime lastOnlineTime,
        Boolean secretConfigured
) {
}
