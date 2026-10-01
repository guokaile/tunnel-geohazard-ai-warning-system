package com.tgaws.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgaws.business.sys.auth.AuthService;
import com.tgaws.business.sys.auth.UserRoleService;
import com.tgaws.business.sys.token.JwtTokenProvider;
import com.tgaws.common.result.ErrorCode;
import com.tgaws.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证与权限点拦截器（T-701，《6》6.8）：
 *
 * <ul>
 *   <li>Bearer 校验：缺失/无效/黑名单 → 401 C0006（S-04 验收）；</li>
 *   <li>授权装载：UserRoleService（权限点/角色/隧道，60s 缓存）→ LoginContext；</li>
 *   <li>{@link RequirePermission} 校验：无权限点 → 403 C0008（S-03 验收）。</li>
 * </ul>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER = "Bearer ";

    private final AuthService authService;
    private final UserRoleService userRoleService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthInterceptor(AuthService authService, UserRoleService userRoleService) {
        this.authService = authService;
        this.userRoleService = userRoleService;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        JwtTokenProvider.ParsedToken token = null;
        if (authorization != null && authorization.startsWith(BEARER)) {
            token = authService.verifyAccess(authorization.substring(BEARER.length()));
        }
        // SSE（EventSource 无法携带自定义头）经 ?token= 参数认证（《6》6.3.8）
        if (token == null) {
            String queryToken = request.getParameter("token");
            if (queryToken != null && !queryToken.isBlank()) {
                token = authService.verifyAccess(queryToken);
            }
        }
        if (token == null) {
            return reject(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.C0006);
        }
        UserRoleService.Authorities authorities = userRoleService.authorities(token.userId());
        LoginContext.setUser(new LoginContext.LoginUser(token.userId(), token.username(),
                authorities.permissions(), authorities.roles(),
                authorities.tunnelIds(), authorities.maxScope()));

        RequirePermission required = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (required != null && !authorities.permissions().contains(required.value())) {
            return reject(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.C0008);
        }
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                @NonNull Object handler, Exception ex) {
        LoginContext.clear();
    }

    /** 直接写响应（拦截器不经过 GlobalExceptionHandler） */
    private boolean reject(HttpServletResponse response, int status, ErrorCode errorCode) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), Result.fail(errorCode));
        return false;
    }
}
