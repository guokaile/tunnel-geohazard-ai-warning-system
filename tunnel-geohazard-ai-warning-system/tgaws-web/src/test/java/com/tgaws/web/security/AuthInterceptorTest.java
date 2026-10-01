package com.tgaws.web.security;

import com.tgaws.business.sys.auth.AuthService;
import com.tgaws.business.sys.auth.UserRoleService;
import com.tgaws.business.sys.token.JwtTokenProvider;
import com.tgaws.common.result.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 认证与权限点拦截器单测（T-701 验收：未认证 401 / 无权限 403 / SSE ?token= 兜底）。
 */
class AuthInterceptorTest {

    private AuthService authService;
    private UserRoleService userRoleService;
    private AuthInterceptor interceptor;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        userRoleService = mock(UserRoleService.class);
        interceptor = new AuthInterceptor(authService, userRoleService);
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        LoginContext.clear();
    }

    private JwtTokenProvider.ParsedToken token(long userId, String username) {
        return new JwtTokenProvider.ParsedToken(userId, username, "jti-1", Instant.now().plusSeconds(600));
    }

    private HandlerMethod handler() throws NoSuchMethodException {
        return new HandlerMethod(new StubController(), StubController.class.getMethod("needPermission"));
    }

    /** 无注记接口：仅认证即可 */
    private HandlerMethod openHandler() throws NoSuchMethodException {
        return new HandlerMethod(new StubController(), StubController.class.getMethod("noPermission"));
    }

    @Test
    void missingTokenRejected401() throws Exception {
        boolean ok = interceptor.preHandle(new MockHttpServletRequest(), response, handler());
        assertFalse(ok);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains(ErrorCode.C0006.getCode()));
    }

    @Test
    void blacklistedTokenRejected401() throws Exception {
        when(authService.verifyAccess("bad")).thenReturn(null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bad");
        boolean ok = interceptor.preHandle(request, response, handler());
        assertFalse(ok);
        assertEquals(401, response.getStatus(), "黑名单/无效 token → 401 C0006");
    }

    @Test
    void validTokenButNoPermissionRejected403() throws Exception {
        when(authService.verifyAccess("ok")).thenReturn(token(7L, "disp01"));
        when(userRoleService.authorities(7L)).thenReturn(new UserRoleService.Authorities(
                Set.of("warn:event:view"), Set.of("DISPATCHER"), Set.of(1L), 2));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer ok");
        boolean ok = interceptor.preHandle(request, response, handler());
        assertFalse(ok, "needPermission 接口无对应权限点 → 403");
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains(ErrorCode.C0008.getCode()));
    }

    @Test
    void validTokenWithPermissionPasses() throws Exception {
        when(authService.verifyAccess("ok")).thenReturn(token(7L, "disp01"));
        when(userRoleService.authorities(7L)).thenReturn(new UserRoleService.Authorities(
                Set.of("warn:event:close"), Set.of("DISPATCHER"), Set.of(1L), 2));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer ok");
        assertTrue(interceptor.preHandle(request, response, handler()));
        LoginContext.LoginUser user = LoginContext.getUser();
        assertNotNull(user);
        assertEquals(7L, user.userId());
        assertEquals("disp01", user.username());
        assertEquals(Set.of(1L), user.tunnelIds());
    }

    @Test
    void unannotatedEndpointPassesWithAuth() throws Exception {
        when(authService.verifyAccess("ok")).thenReturn(token(7L, "disp01"));
        when(userRoleService.authorities(7L)).thenReturn(new UserRoleService.Authorities(
                Set.of(), Set.of("DISPATCHER"), Set.of(), 2));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer ok");
        assertTrue(interceptor.preHandle(request, response, openHandler()));
    }

    @Test
    void sseQueryTokenFallback() throws Exception {
        // EventSource 无法携带 Authorization 头 → ?token= 兜底（《6》6.3.8）
        when(authService.verifyAccess("q-token")).thenReturn(token(8L, "monitor01"));
        when(userRoleService.authorities(8L)).thenReturn(new UserRoleService.Authorities(
                Set.of(), Set.of("INSPECTOR"), Set.of(), 2));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/warn/stream");
        request.setParameter("token", "q-token");
        assertTrue(interceptor.preHandle(request, response, openHandler()));
        assertEquals(8L, LoginContext.getUser().userId());
    }

    @Test
    void afterCompletionClearsContext() throws Exception {
        when(authService.verifyAccess("ok")).thenReturn(token(7L, "disp01"));
        when(userRoleService.authorities(7L)).thenReturn(new UserRoleService.Authorities(
                Set.of(), Set.of("DISPATCHER"), Set.of(), 2));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer ok");
        interceptor.preHandle(request, response, openHandler());
        assertNotNull(LoginContext.getUser());
        interceptor.afterCompletion(request, response, openHandler(), null);
        assertTrue(LoginContext.getUser() == null, "ThreadLocal 请求结束必须清理");
    }

    @Test
    void authorizeLoadedFromContextNotTrustedBody() {
        // W7 口径：userId 只能来自认证上下文（ScopeGuard.userId 读取 LoginContext）
        LoginContext.setUser(new LoginContext.LoginUser(9L, "u", Set.of(), Set.of(), Set.of(), 2));
        assertEquals(9L, ScopeGuard.userId());
        assertEquals("u", ScopeGuard.username());
    }

    static class StubController {
        @RequirePermission("warn:event:close")
        public void needPermission() {
        }

        public void noPermission() {
        }
    }
}
