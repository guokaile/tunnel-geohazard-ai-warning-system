package com.tgaws.common.datascope;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据权限强类型条件构建单测（评审定稿方案：参数化 IN 条件，不做 SQL 字符串拼接）。
 */
class DataScopeHelperTest {

    @Test
    void allScopeReturnsNullCondition() {
        DataScopeCondition cond = DataScopeHelper.forTunnel(DataScope.all());
        assertNull(cond, "type=1 全部数据不追加条件");
    }

    @Test
    void tunnelScopeBuildsParameterizedIn() {
        DataScope scope = DataScope.ofTunnels(Set.of(1L, 2L, 3L));
        DataScopeCondition cond = DataScopeHelper.forTunnel(scope);
        assertTrue(cond != null);
        assertEquals("tunnel_id IN (?,?,?)", cond.toSql());
        assertEquals(3, cond.params().size());
        assertFalse(cond.isEmpty());
    }

    @Test
    void emptyTunnelScopeMeansNoAccess() {
        DataScope scope = DataScope.ofTunnels(Set.of());
        DataScopeCondition cond = DataScopeHelper.forTunnel(scope);
        assertTrue(cond != null);
        assertEquals("1=0", cond.toSql());
        assertTrue(cond.isEmpty());
    }

    @Test
    void selfScopeOnTunnelColumnMeansNoAccess() {
        DataScope scope = DataScope.self(9L);
        DataScopeCondition cond = DataScopeHelper.forTunnel(scope);
        assertTrue(cond != null && cond.isEmpty());
    }

    @Test
    void personScopeBuildsSingleParam() {
        DataScope scope = DataScope.self(9L);
        DataScopeCondition cond = DataScopeHelper.forPerson(scope, "assignee_id");
        assertTrue(cond != null);
        assertEquals("assignee_id IN (?)", cond.toSql());
        assertEquals(9L, cond.params().get(0));
    }

    @Test
    void checkTunnelGuard() {
        DataScope all = DataScope.all();
        DataScope t1 = DataScope.ofTunnels(Set.of(1L));
        assertTrue(DataScopeHelper.checkTunnel(all, 99L));
        assertTrue(DataScopeHelper.checkTunnel(t1, 1L));
        assertFalse(DataScopeHelper.checkTunnel(t1, 2L));
        assertFalse(DataScopeHelper.checkTunnel(DataScope.self(9L), 1L));
    }

    @Test
    void tunnelListScopeUsesIdColumn() {
        DataScopeCondition cond = DataScopeHelper.forTunnelList(DataScope.ofTunnels(Set.of(4L, 5L)));
        assertTrue(cond != null);
        assertEquals("id IN (?,?)", cond.toSql());
        assertEquals(2, cond.params().size());
    }

    @Test
    void tunnelListScopeAllReturnsNull() {
        assertNull(DataScopeHelper.forTunnelList(DataScope.all()));
    }

    @Test
    void tunnelListScopeSelfMeansNoAccess() {
        DataScopeCondition cond = DataScopeHelper.forTunnelList(DataScope.self(9L));
        assertTrue(cond != null && cond.isEmpty());
    }
}
