package com.tgaws.web.config;

import com.tgaws.access.tcp.auth.GatewayOnlineRegistry;
import com.tgaws.business.mon.manager.GatewayOnlineStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 网关在线扫描（《6》6.3.3「数据中断检测」任务接线，每 1min）：
 *
 * <p>扫描在线注册表（access）：超 180s 无心跳 → 置离线，经
 * GatewayOnlineStatusService（business）写库——web 不直触 Mapper（T-704 分层）；
 * 全网关离线（含空表判空语义）→ D0006 数据中断告警。</p>
 */
@Component
public class GatewayOnlineScanner {

    private static final Logger log = LoggerFactory.getLogger(GatewayOnlineScanner.class);

    /** 离线判定阈值（秒，与 sys_config protocol.offline_sec 一致） */
    private static final long OFFLINE_SEC = 180L;

    private final GatewayOnlineRegistry onlineRegistry;
    private final GatewayOnlineStatusService statusService;

    public GatewayOnlineScanner(GatewayOnlineRegistry onlineRegistry,
                                GatewayOnlineStatusService statusService) {
        this.onlineRegistry = onlineRegistry;
        this.statusService = statusService;
    }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void scan() {
        Set<String> gatewayCodes = onlineRegistry.gatewayCodes();
        if (gatewayCodes.isEmpty()) {
            return; // 无登记网关：空转（TCP 服务未启用或尚无注册）
        }
        long now = System.currentTimeMillis();
        int offlineCount = 0;
        for (String code : gatewayCodes) {
            boolean offline = onlineRegistry.isOffline(code, now, OFFLINE_SEC);
            statusService.updateOnlineStatus(code, offline ? 0 : 1);
            if (offline) {
                offlineCount++;
            }
        }
        if (offlineCount == gatewayCodes.size()) {
            log.error("D0006 数据中断：全部 {} 个网关离线超 {}s", offlineCount, OFFLINE_SEC);
        }
    }
}
