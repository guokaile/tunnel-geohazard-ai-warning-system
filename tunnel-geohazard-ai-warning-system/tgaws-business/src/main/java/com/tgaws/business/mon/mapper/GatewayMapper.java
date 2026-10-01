package com.tgaws.business.mon.mapper;

import com.tgaws.business.mon.entity.GatewayEntity;
import org.apache.ibatis.annotations.Param;

/**
 * 采集网关 Mapper（mon_gateway：注册凭据查询 + 在线状态维护）。
 */
public interface GatewayMapper {

    /** 按网关编号查启用网关（注册认证用：secret 密文 + status） */
    GatewayEntity selectByCode(@Param("gatewayCode") String gatewayCode);

    /** 更新在线状态（0离线 1在线，心跳/离线扫描维护） */
    int updateOnlineStatus(@Param("gatewayCode") String gatewayCode, @Param("onlineStatus") int onlineStatus);
}
