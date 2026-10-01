package com.tgaws.business.mon.manager;

import com.tgaws.business.mon.mapper.GatewayMapper;
import org.springframework.stereotype.Service;

/**
 * 网关在线状态写入（T-704 分层：web 扫描器经本服务写库，不直触 Mapper）。
 */
@Service
public class GatewayOnlineStatusService {

    private final GatewayMapper gatewayMapper;

    public GatewayOnlineStatusService(GatewayMapper gatewayMapper) {
        this.gatewayMapper = gatewayMapper;
    }

    public void updateOnlineStatus(String gatewayCode, int onlineStatus) {
        gatewayMapper.updateOnlineStatus(gatewayCode, onlineStatus);
    }
}
