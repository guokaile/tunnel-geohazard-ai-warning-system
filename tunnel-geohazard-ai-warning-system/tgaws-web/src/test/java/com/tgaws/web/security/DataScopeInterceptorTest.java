package com.tgaws.web.security;

import com.tgaws.business.sys.auth.UserRoleService;
import com.tgaws.common.datascope.DataScope;
import com.tgaws.common.exception.BizException;
import com.tgaws.common.result.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 数据权限拦截器与出口守卫单测（T-701 验收：越权访问他隧道数据 → C0008）。
 */
class DataScopeInterceptorTest {

    private UserRoleService userRoleService;
    private DataScopeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        userRoleService = mock(UserRoleService.class);
        interceptor = new DataScopeInterceptor(userRoleService);
    }

    @AfterEach
    void tearDown() {
        LoginContext.clear();
    }

    @Test
    void assemblesScopeFromLoginUser() {
        LoginContext.setUser(new LoginContext.LoginUser(7L, "disp01",
                Set.of(), Set.of("DISPATCHER"), Set.of(1L, 2L), 2));
        when(userRoleService.buildScope(any(UserRoleService.Authorities.class), eq(7L)))
                .thenReturn(DataScope.ofTunnels(Set.of(1L, 2L)));
        assertTrue(interceptor.preHandle(new MockHttpServletRequest(),
                new MockHttpServletResponse(), new Object()));
        assertEquals(DataScope.TYPE_TUNNEL, LoginContext.getScope().getType());
        assertEquals(Set.of(1L, 2L), LoginContext.getScope().getTunnelIds());
    }

    @Test
    void noLoginUserPassesThrough() {
        // 未登录由 AuthInterceptor 拒绝；本拦截器不重复处理
        assertTrue(interceptor.preHandle(new MockHttpServletRequest(),
                new MockHttpServletResponse(), new Object()));
        assertEquals(null, LoginContext.getScope());
    }

    @Test
    void requireTunnelRejectsForeignTunnel() {
        LoginContext.setScope(DataScope.ofTunnels(Set.of(1L, 2L)));
        assertEquals(ErrorCode.C0008, assertThrows(BizException.class, () ->
                ScopeGuard.requireTunnel(9L)).getErrorCode(), "越权访问他隧道 → C0008");
    }

    @Test
    void requireTunnelAllowsGrantedTunnel() {
        LoginContext.setScope(DataScope.ofTunnels(Set.of(1L, 2L)));
        ScopeGuard.requireTunnel(2L);
        assertEquals(ErrorCode.C0008, assertThrows(BizException.class, () ->
                ScopeGuard.requireTunnel(null)).getErrorCode(), "目标隧道为空 → C0008");
    }

    @Test
    void requireSelfAllowsOnlySelf() {
        LoginContext.setScope(DataScope.self(5L));
        ScopeGuard.requireSelf(5L);
        assertEquals(ErrorCode.C0008, assertThrows(BizException.class, () ->
                ScopeGuard.requireSelf(6L)).getErrorCode(), "仅本人范围不得操作他人任务");
    }

    @Test
    void requireSelfAllTypeAllowsAny() {
        LoginContext.setScope(DataScope.all());
        ScopeGuard.requireSelf(6L);
    }

    @Test
    void userIdFromContextNotBody() {
        LoginContext.setUser(new LoginContext.LoginUser(9L, "u", Set.of(), Set.of(), Set.of(), 2));
        assertEquals(9L, ScopeGuard.userId());
        LoginContext.clear();
        assertEquals(ErrorCode.C0006, assertThrows(BizException.class, ScopeGuard::userId)
                .getErrorCode(), "无登录态取 userId → C0006");
    }
}
