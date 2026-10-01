package com.tgaws.access.tcp.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网关在线注册表单测（离线判定阈值 180s，与 sys_config protocol.offline_sec 一致）。
 */
class GatewayOnlineRegistryTest {

    @Test
    void onlineOfflineLifecycle() {
        GatewayOnlineRegistry registry = new GatewayOnlineRegistry();
        long now = System.currentTimeMillis();
        registry.markOnline("GW001", now);
        registry.markOnline("GW002", now);

        assertEquals(now, registry.lastSeenOf("GW001"));
        assertEquals(2, registry.registeredCount());
        assertEquals(2, registry.countOnline(now, 180));
        assertFalse(registry.isOffline("GW001", now, 180));

        // 模拟 181 秒无心跳 → 离线
        long later = now + 181_000L;
        assertTrue(registry.isOffline("GW001", later, 180));
        assertEquals(0, registry.countOnline(later, 180));

        // 未登记网关视为离线
        assertTrue(registry.isOffline("GW999", later, 180));
    }
}
