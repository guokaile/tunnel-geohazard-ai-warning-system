package com.tgaws.common.datascope;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据权限构建器（《6.详细设计说明书》6.3.6 强类型显式方案）。
 *
 * <p>使用约定：</p>
 * <ul>
 *   <li>查询路径：Mapper 查询方法签名强制携带 {@link DataScopeCondition} 参数，
 *       Manager 层调用 {@link #forTunnel}/{@link #forSection}/{@link #forPerson} 构建；
 *       返回 null 表示"全部数据，不追加条件"（type=1）。</li>
 *   <li>写/改/删路径：一律先按 scope 条件查主键（查不到即 C0008），禁止"查出后再比对"。</li>
 * </ul>
 */
public final class DataScopeHelper {

    private DataScopeHelper() {
    }

    /**
     * 隧道级条件。
     *
     * @param scope 数据范围
     * @return null=全部数据；否则为 tunnel_id IN (...) 条件（空集合 → 1=0 无权限）
     */
    public static DataScopeCondition forTunnel(DataScope scope) {
        if (scope == null || scope.getType() == DataScope.TYPE_ALL) {
            return null;
        }
        if (scope.getType() == DataScope.TYPE_SELF) {
            return DataScopeCondition.of("tunnel_id", List.of());
        }
        return DataScopeCondition.of("tunnel_id", new ArrayList<>(scope.getTunnelIds()));
    }

    /**
     * 断面级条件。
     *
     * @param scope 数据范围
     * @return null=全部数据；否则为 section_id IN (...) 条件
     */
    public static DataScopeCondition forSection(DataScope scope) {
        if (scope == null || scope.getType() == DataScope.TYPE_ALL) {
            return null;
        }
        if (scope.getType() == DataScope.TYPE_SELF) {
            return DataScopeCondition.of("section_id", List.of());
        }
        return DataScopeCondition.of("section_id", new ArrayList<>(scope.getSectionIds()));
    }

    /**
     * 仅本人条件（type=4，用于任务/记录类数据）。
     *
     * @param scope 数据范围
     * @return null=全部数据；否则为 person_column = ? 条件
     */
    public static DataScopeCondition forPerson(DataScope scope, String personColumn) {
        if (scope == null || scope.getType() == DataScope.TYPE_ALL) {
            return null;
        }
        if (scope.getType() != DataScope.TYPE_SELF || scope.getUserId() == null) {
            return DataScopeCondition.of(personColumn, List.of());
        }
        return DataScopeCondition.of(personColumn, List.of(scope.getUserId()));
    }

    /**
     * 隧道台账列表条件（prj_tunnel.id 列；与 {@link #forTunnel} 的 tunnel_id 列区分，
     * 供 B01 隧道列表等按主键过滤的查询使用）。
     *
     * @param scope 数据范围
     * @return null=全部数据；否则为 id IN (...) 条件（空集合 → 1=0 无权限）
     */
    public static DataScopeCondition forTunnelList(DataScope scope) {
        if (scope == null || scope.getType() == DataScope.TYPE_ALL) {
            return null;
        }
        if (scope.getType() == DataScope.TYPE_SELF) {
            return DataScopeCondition.of("id", List.of());
        }
        return DataScopeCondition.of("id", new ArrayList<>(scope.getTunnelIds()));
    }

    /**
     * 数据出口校验：详情/操作类接口在 service 入口调用，不通过返回 C0008。
     *
     * @param scope    数据范围
     * @param tunnelId 目标隧道 id
     * @return true=允许访问
     */
    public static boolean checkTunnel(DataScope scope, Long tunnelId) {
        if (scope == null || tunnelId == null) {
            return false;
        }
        return switch (scope.getType()) {
            case DataScope.TYPE_ALL -> true;
            case DataScope.TYPE_TUNNEL, DataScope.TYPE_SECTION -> scope.getTunnelIds().contains(tunnelId);
            default -> false;
        };
    }
}
