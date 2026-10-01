package com.tgaws.web.log;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计注记（T-703，8.8 S-08）：
 * OperLogAspect 拦截后异步脱敏落 sys_oper_log（仅追加，哈希链防篡改）。
 * 登录接口不标注（登录日志走 sys_login_log，T-601）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperLog {

    /** 操作内容，如 "创建巡检计划" */
    String value();

    /** 业务模块，如 "巡检管理" */
    String module() default "系统";
}
