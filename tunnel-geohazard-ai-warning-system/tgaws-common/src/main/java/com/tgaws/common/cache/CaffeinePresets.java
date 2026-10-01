package com.tgaws.common.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;

/**
 * Caffeine 构建预置（《6.详细设计说明书》6.3.4 统一 TTL/容量口径，
 * 由 web 层 CacheConfig 装配；二期切换 Redis 走 Spring Cache 抽象）。
 */
public final class CaffeinePresets {

    private CaffeinePresets() {
    }

    /** 短 TTL（看板最新值）：5s */
    public static <K, V> Cache<K, V> shortTtlCache(long maxSize) {
        return Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(Duration.ofSeconds(5))
                .build();
    }

    /** 中 TTL（字典/配置/规则/点位/模型）：10min */
    public static <K, V> Cache<K, V> mediumTtlCache(long maxSize) {
        return Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(Duration.ofMinutes(10))
                .build();
    }

    /** 会话级 TTL（黑名单/nonce/登录失败）：30min 或 5min */
    public static <K, V> Cache<K, V> sessionTtlCache(long maxSize, Duration ttl) {
        return Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttl)
                .build();
    }

    /** 长 TTL（幂等结果）：24h */
    public static <K, V> Cache<K, V> dayTtlCache(long maxSize) {
        return Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(Duration.ofHours(24))
                .build();
    }
}
