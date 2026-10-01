package com.tgaws.business.sys.auth;

import com.tgaws.business.sys.mapper.UserMapper;
import com.tgaws.common.datascope.DataScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 授权装载单测（T-701：三集合装载 + 60s 缓存 + DataScope 组装口径）。
 */
class UserRoleServiceTest {

    private UserMapper userMapper;
    private UserRoleService service;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        service = new UserRoleService(userMapper);
    }

    @Test
    void authoritiesLoadsAllThreeSets() {
        when(userMapper.selectPermissionsByUser(7L)).thenReturn(Set.of("warn:event:view", "patrol:task:fill"));
        when(userMapper.selectRoleCodesByUser(7L)).thenReturn(Set.of("DISPATCHER"));
        when(userMapper.selectTunnelIdsByUser(7L)).thenReturn(Set.of(1L, 2L));
        when(userMapper.selectMaxDataScopeByUser(7L)).thenReturn(2);
        UserRoleService.Authorities a = service.authorities(7L);
        assertTrue(a.permissions().contains("warn:event:view"));
        assertEquals(Set.of(1L, 2L), a.tunnelIds());
        assertEquals(2, a.maxScope());
    }

    @Test
    void authoritiesCachedWithin60s() {
        when(userMapper.selectPermissionsByUser(7L)).thenReturn(Set.of("a"));
        when(userMapper.selectRoleCodesByUser(7L)).thenReturn(Set.of("R"));
        when(userMapper.selectTunnelIdsByUser(7L)).thenReturn(Set.of());
        when(userMapper.selectMaxDataScopeByUser(7L)).thenReturn(2);
        service.authorities(7L);
        service.authorities(7L);
        verify(userMapper, times(1)).selectPermissionsByUser(7L);
    }

    @Test
    void buildScopeAdminRoleGetsAll() {
        UserRoleService.Authorities a = new UserRoleService.Authorities(
                Set.of("*"), Set.of("ADMIN"), Set.of(), 1);
        DataScope scope = service.buildScope(a, 1L);
        assertEquals(DataScope.TYPE_ALL, scope.getType());
    }

    @Test
    void buildScopeTypeAllFromDataScopeColumn() {
        // EXPERT/LEADER 种子 data_scope=1 → 全部
        UserRoleService.Authorities a = new UserRoleService.Authorities(
                Set.of(), Set.of("EXPERT"), Set.of(), 1);
        assertEquals(DataScope.TYPE_ALL, service.buildScope(a, 3L).getType());
    }

    @Test
    void buildScopeTunnelFromGrants() {
        // DISPATCHER data_scope=2 本隧道：隧道集合取 sys_role_tunnel 授权
        UserRoleService.Authorities a = new UserRoleService.Authorities(
                Set.of(), Set.of("DISPATCHER"), Set.of(1L, 3L), 2);
        DataScope scope = service.buildScope(a, 2L);
        assertEquals(DataScope.TYPE_TUNNEL, scope.getType());
        assertEquals(Set.of(1L, 3L), scope.getTunnelIds());
    }

    @Test
    void buildScopeSectionDegradesToTunnel() {
        // data_scope=3 本断面：无断面授权表，按本隧道口径降级（《6》6.8）
        UserRoleService.Authorities a = new UserRoleService.Authorities(
                Set.of(), Set.of("INSPECTOR"), Set.of(2L), 3);
        DataScope scope = service.buildScope(a, 2L);
        assertEquals(DataScope.TYPE_TUNNEL, scope.getType());
        assertEquals(Set.of(2L), scope.getTunnelIds());
    }

    @Test
    void buildScopeSelfAndNoRole() {
        UserRoleService.Authorities self = new UserRoleService.Authorities(
                Set.of(), Set.of("MONITOR"), Set.of(), 4);
        DataScope scope = service.buildScope(self, 5L);
        assertEquals(DataScope.TYPE_SELF, scope.getType());
        assertEquals(5L, scope.getUserId());

        // 无任何角色（maxScope=null）：最小可用（仅本人），防越权放大
        UserRoleService.Authorities none = new UserRoleService.Authorities(
                Set.of(), Set.of(), Set.of(), null);
        DataScope scope2 = service.buildScope(none, 6L);
        assertEquals(DataScope.TYPE_SELF, scope2.getType());
        assertEquals(6L, scope2.getUserId());
    }

    @Test
    void hasPermissionChecksSet() {
        UserRoleService.Authorities a = new UserRoleService.Authorities(
                Set.of("warn:event:close"), Set.of(), Set.of(), 2);
        assertTrue(service.hasPermission(a, "warn:event:close"));
        assertFalse(service.hasPermission(a, "warn:event:dispatch"));
    }
}
