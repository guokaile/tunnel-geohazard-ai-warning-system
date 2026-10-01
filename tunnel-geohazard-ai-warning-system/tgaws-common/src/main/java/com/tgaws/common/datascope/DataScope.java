package com.tgaws.common.datascope;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

/**
 * 数据权限范围（对应《4.接口设计说明书》4.7 与《6.详细设计说明书》6.3.6）。
 *
 * <p>由 web 层 DataScopeInterceptor 仅负责解析登录态生成，不注入查询条件；
 * 查询条件由 {@link DataScopeHelper} 显式构建强类型 {@link DataScopeCondition}。</p>
 */
public class DataScope implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 数据范围类型：1全部 2本隧道 3本断面 4仅本人 */
    public static final int TYPE_ALL = 1;
    public static final int TYPE_TUNNEL = 2;
    public static final int TYPE_SECTION = 3;
    public static final int TYPE_SELF = 4;

    /** 范围类型 */
    private final int type;

    /** 授权隧道 id 集合（type=2/3 时有效） */
    private final Set<Long> tunnelIds;

    /** 授权断面 id 集合（type=3 时有效） */
    private final Set<Long> sectionIds;

    /** 当前用户 id（type=4 时有效） */
    private final Long userId;

    private DataScope(int type, Set<Long> tunnelIds, Set<Long> sectionIds, Long userId) {
        this.type = type;
        this.tunnelIds = tunnelIds == null ? Collections.emptySet() : tunnelIds;
        this.sectionIds = sectionIds == null ? Collections.emptySet() : sectionIds;
        this.userId = userId;
    }

    /** 全部数据（管理员/专家/领导默认） */
    public static DataScope all() {
        return new DataScope(TYPE_ALL, null, null, null);
    }

    /** 限定隧道集合 */
    public static DataScope ofTunnels(Set<Long> tunnelIds) {
        return new DataScope(TYPE_TUNNEL, tunnelIds, null, null);
    }

    /** 限定断面集合（同时携带所属隧道） */
    public static DataScope ofSections(Set<Long> tunnelIds, Set<Long> sectionIds) {
        return new DataScope(TYPE_SECTION, tunnelIds, sectionIds, null);
    }

    /** 仅本人数据 */
    public static DataScope self(Long userId) {
        return new DataScope(TYPE_SELF, null, null, userId);
    }

    public int getType() {
        return type;
    }

    public Set<Long> getTunnelIds() {
        return tunnelIds;
    }

    public Set<Long> getSectionIds() {
        return sectionIds;
    }

    public Long getUserId() {
        return userId;
    }
}
