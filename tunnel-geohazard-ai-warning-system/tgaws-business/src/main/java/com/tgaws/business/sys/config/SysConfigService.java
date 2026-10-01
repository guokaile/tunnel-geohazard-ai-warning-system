package com.tgaws.business.sys.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tgaws.business.sys.mapper.SysConfigMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 系统配置服务（60s 缓存；sys_config 缺失/未配置返回 null，调用方走默认值）。
 */
@Service
public class SysConfigService {

    private final SysConfigMapper sysConfigMapper;
    private final Cache<String, String> cache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofSeconds(60))
            .build();

    public SysConfigService(SysConfigMapper sysConfigMapper) {
        this.sysConfigMapper = sysConfigMapper;
    }

    public String get(String key) {
        return cache.get(key, sysConfigMapper::selectByKey);
    }

    /** 整数值（缺省/非法回退默认值） */
    public int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
