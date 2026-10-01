package com.tgaws.web.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口权限点（《4》4.7 权限矩阵）：AuthInterceptor 校验，缺失或无权限 → 403 C0008。
 * 白名单接口（auth/login 等）不加注记。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** 权限点编码，如 warn:event:close */
    String value();
}
