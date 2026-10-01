package com.tgaws.common.datascope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * 数据权限强类型条件对象（评审定稿方案，对应《6.详细设计说明书》6.3.6）。
 *
 * <p>内部持有参数化 IN 条件（? 占位符 + 值列表），不做 SQL 字符串拼接；
 * Mapper 查询方法签名强制携带本参数，漏用即编译失败；
 * 由 {@link DataScopeHelper} 构建。</p>
 */
public class DataScopeCondition {

    /** 条件列名（白名单来源：DataScopeHelper 固定列） */
    private final String column;

    /** 参数化值列表 */
    private final List<Long> ids;

    /** 空集合语义：命中零行（无权限），生成 1=0 条件 */
    private final boolean emptyResult;

    private DataScopeCondition(String column, List<Long> ids) {
        this.column = column;
        this.ids = ids == null ? Collections.emptyList() : ids;
        this.emptyResult = this.ids.isEmpty();
    }

    static DataScopeCondition of(String column, List<Long> ids) {
        Objects.requireNonNull(column, "column must not be null");
        return new DataScopeCondition(column, ids);
    }

    /**
     * 生成参数化 SQL 片段（如 "tunnel_id IN (?,?,?)"；空集合为 "1=0"）。
     * 仅由 Mapper XML/注解 SQL 拼接静态结构，值一律走 {@link #params()}。
     */
    public String toSql() {
        if (emptyResult) {
            return "1=0";
        }
        StringJoiner sj = new StringJoiner(",", column + " IN (", ")");
        for (int i = 0; i < ids.size(); i++) {
            sj.add("?");
        }
        return sj.toString();
    }

    /** 参数化值（与 toSql 占位符一一对应） */
    public List<Long> params() {
        return ids;
    }

    /** 是否命中零行（无任何授权） */
    public boolean isEmpty() {
        return emptyResult;
    }

    public String getColumn() {
        return column;
    }

    /** 供测试与排查使用的值快照（不可变） */
    public List<Long> snapshotIds() {
        return new ArrayList<>(ids);
    }
}
