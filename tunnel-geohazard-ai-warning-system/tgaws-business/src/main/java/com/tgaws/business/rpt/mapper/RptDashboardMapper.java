package com.tgaws.business.rpt.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 驾驶舱实时段查询（FR-802：仅查"今日/近24h"有界窗口，走 idx_create_time 等索引；
 * 完整日指标一律走 rpt_stat_daily 预聚合，实时 GROUP BY 扫千万行不可能 ≤1min 达标）。
 */
public interface RptDashboardMapper {

    /** 今日预警数（可按级别过滤：level=null 计全部） */
    long countWarnByRange(@Param("tunnelId") long tunnelId,
                          @Param("from") LocalDateTime from,
                          @Param("level") Integer level);

    /** 未闭环预警数（1待确认/2已确认/3处置中/4待复核） */
    long countOpenEvents(@Param("tunnelId") long tunnelId);

    /** 今日闭环处置任务数（finish_time 口径） */
    long countDisposeClosedByRange(@Param("tunnelId") long tunnelId,
                                   @Param("from") LocalDateTime from);

    /** 未闭环处置任务数（1待处置/2处置中） */
    long countDisposeOpen(@Param("tunnelId") long tunnelId);

    /** 网关在线率分子分母（网关无隧道归属：全局口径） */
    Map<String, Object> gatewayStatus();

    /** 高风险点位 TOP10（近24h 橙/红级事件） */
    List<Map<String, Object>> topRiskPoints(@Param("tunnelId") long tunnelId,
                                            @Param("from") LocalDateTime from);
}
