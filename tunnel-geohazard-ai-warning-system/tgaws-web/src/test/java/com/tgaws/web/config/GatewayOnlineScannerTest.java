package com.tgaws.web.config;

import com.tgaws.access.tcp.auth.GatewayOnlineRegistry;
import com.tgaws.business.mon.manager.GatewayOnlineStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 网关在线扫描单测（《6》6.3.3 数据中断检测接线，W4-4c）。
 */
class GatewayOnlineScannerTest {

    private GatewayOnlineRegistry registry;
    private GatewayOnlineStatusService statusService;
    private GatewayOnlineScanner scanner;

    @BeforeEach
    void setUp() {
        registry = new GatewayOnlineRegistry();
        statusService = mock(GatewayOnlineStatusService.class);
        scanner = new GatewayOnlineScanner(registry, statusService);
    }

    @Test
    void emptyRegistryNoOp() {
        scanner.scan();
        verify(statusService, never()).updateOnlineStatus(Mockito.anyString(), Mockito.anyInt());
    }

    @Test
    void offlineGatewayMigratedToOffline() {
        long now = System.currentTimeMillis();
        registry.markOnline("GW001", now - 200_000L); // 超 180s → 离线
        registry.markOnline("GW002", now);            // 存活 → 在线
        scanner.scan();
        verify(statusService).updateOnlineStatus(eq("GW001"), eq(0));
        verify(statusService).updateOnlineStatus(eq("GW002"), eq(1));
    }
}
