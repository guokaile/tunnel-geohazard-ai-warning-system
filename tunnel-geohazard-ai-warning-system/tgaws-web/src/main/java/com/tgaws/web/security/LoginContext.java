package com.tgaws.web.security;

import com.tgaws.common.datascope.DataScope;

import java.util.Set;

/**
 * 登录态请求上下文（T-701）：
 * AuthInterceptor 装载用户+授权，DataScopeInterceptor 装载 DataScope；
 * 请求结束 afterCompletion 清理（ThreadLocal 防串线）。
 */
public final class LoginContext {

    private static final ThreadLocal<LoginUser> USER = new ThreadLocal<>();
    private static final ThreadLocal<DataScope> SCOPE = new ThreadLocal<>();

    private LoginContext() {
    }

    public static void setUser(LoginUser user) {
        USER.set(user);
    }

    public static LoginUser getUser() {
        return USER.get();
    }

    public static void setScope(DataScope scope) {
        SCOPE.set(scope);
    }

    public static DataScope getScope() {
        return SCOPE.get();
    }

    public static void clear() {
        USER.remove();
        SCOPE.remove();
    }

    /**
     * 登录用户快照（授权装载自 UserRoleService，60s 缓存口径）。
     *
     * @param maxScope null=无任何角色
     */
    public record LoginUser(long userId, String username, Set<String> permissions,
                            Set<String> roles, Set<Long> tunnelIds, Integer maxScope) {
    }
}
