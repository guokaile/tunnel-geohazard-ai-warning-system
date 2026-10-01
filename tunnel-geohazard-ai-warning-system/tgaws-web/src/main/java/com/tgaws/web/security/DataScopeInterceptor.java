package com.tgaws.web.security;

import com.tgaws.business.sys.auth.UserRoleService;
import com.tgaws.common.datascope.DataScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 数据权限拦截器（T-701，《6》6.3.6 显式方案）：
 * <b>仅负责</b>登录态 → DataScope 请求上下文组装，<b>不注入查询条件</b>；
 * 查询条件由 Manager 层经 DataScopeHelper 显式构建（ArchUnit 守护，T-704）。
 */
@Component
public class DataScopeInterceptor implements HandlerInterceptor {

    private final UserRoleService userRoleService;

    public DataScopeInterceptor(UserRoleService userRoleService) {
        this.userRoleService = userRoleService;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        LoginContext.LoginUser user = LoginContext.getUser();
        if (user == null) {
            return true; // 未登录由 AuthInterceptor 拒绝
        }
        DataScope scope = userRoleService.buildScope(
                new UserRoleService.Authorities(user.permissions(), user.roles(),
                        user.tunnelIds(), user.maxScope()),
                user.userId());
        LoginContext.setScope(scope);
        return true;
    }
}
