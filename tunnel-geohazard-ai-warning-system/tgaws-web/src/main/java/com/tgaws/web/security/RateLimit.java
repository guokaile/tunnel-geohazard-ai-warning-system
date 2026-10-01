package com.tgaws.web.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口分级限流注记（T-703，《6》6.8 分级口径）：
 * 阈值默认按级内建（sys_config 键 rate.limit.{tier} 可覆盖，60s 缓存）；
 * 维度：已登录按 userId，未登录（登录接口）按 IP；超限 HTTP 429。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 限流级：看板 10/s、查询 5/s、写 2/s、登录 5/min */
    Tier value();

    enum Tier {
        DASHBOARD, QUERY, WRITE, LOGIN
    }
}
