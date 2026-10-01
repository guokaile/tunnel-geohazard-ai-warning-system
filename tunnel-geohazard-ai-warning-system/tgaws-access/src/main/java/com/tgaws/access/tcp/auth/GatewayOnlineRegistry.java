package com.tgaws.access.tcp.auth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 网关在线注册表（内存维护：心跳刷新最近在线时间，供离线判定与数据中断自监控 D0006 联动）。
 */
public class GatewayOnlineRegistry {

    private final Map<String, AtomicLong> lastSeen = new ConcurrentHashMap<>();

    /** 心跳/注册成功时刷新在线时间 */
    public void markOnline(String gatewayCode, long tsMs) {
        lastSeen.computeIfAbsent(gatewayCode, k -> new AtomicLong()).set(tsMs);
    }

    /** 最近在线时间；未登记返回 -1 */
    public long lastSeenOf(String gatewayCode) {
        AtomicLong ts = lastSeen.get(gatewayCode);
        return ts == null ? -1L : ts.get();
    }

    /** 是否已离线（超过 offlineSec 秒无心跳） */
    public boolean isOffline(String gatewayCode, long nowMs, long offlineSec) {
        long ts = lastSeenOf(gatewayCode);
        return ts < 0 || nowMs - ts > offlineSec * 1000L;
    }

    /** 当前在线网关数（按离线阈值统计） */
    public long countOnline(long nowMs, long offlineSec) {
        return lastSeen.values().stream()
                .filter(ts -> nowMs - ts.get() <= offlineSec * 1000L)
                .count();
    }

    /** 已登记网关总数 */
    public int registeredCount() {
        return lastSeen.size();
    }

    /** 已登记网关编号快照（离线扫描任务遍历用） */
    public java.util.Set<String> gatewayCodes() {
        return java.util.Set.copyOf(lastSeen.keySet());
    }

    /** 清除（测试用） */
    public void clear() {
        lastSeen.clear();
    }
}
