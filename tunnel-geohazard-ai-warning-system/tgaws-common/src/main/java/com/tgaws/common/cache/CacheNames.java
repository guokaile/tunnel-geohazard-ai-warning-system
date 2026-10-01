package com.tgaws.common.cache;

/**
 * 缓存命名常量（对应《6.详细设计说明书》6.3.4 十个 Caffeine 实例清单）。
 */
public final class CacheNames {

    private CacheNames() {
    }

    /** 点位最新值（TTL 5s，看板轮询口径） */
    public static final String LATEST_VALUE = "latestValue";

    /** 字典（TTL 10min，变更主动失效） */
    public static final String DICT = "dict";

    /** 系统配置（TTL 10min，变更主动失效） */
    public static final String CONFIG = "config";

    /** JWT 登出黑名单（TTL 30min） */
    public static final String TOKEN_BLACKLIST = "tokenBlacklist";

    /** 防重放 nonce（TTL 5min） */
    public static final String NONCE = "nonce";

    /** 登录失败计数（TTL 30min，防爆破） */
    public static final String LOGIN_FAIL = "loginFail";

    /** 幂等结果（TTL 24h） */
    public static final String IDEMPOTENCY = "idempotency";

    /** 启用规则（TTL 10min，变更主动失效） */
    public static final String RULE = "rule";

    /** 点位元数据（TTL 10min，变更主动失效） */
    public static final String POINT_META = "pointMeta";

    /** 模型参数（TTL 10min，变更主动失效） */
    public static final String MODEL_PARAM = "modelParam";
}
