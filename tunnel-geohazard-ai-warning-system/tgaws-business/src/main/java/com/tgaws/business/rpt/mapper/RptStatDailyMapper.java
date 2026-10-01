package com.tgaws.business.rpt.mapper;

import com.tgaws.business.rpt.entity.RptStatDailyEntity;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 报表日聚合 Mapper（rpt_stat_daily，FR-801/802 预聚合口径）。
 * 聚合源查询返回 Map（别名小写驼峰），服务层组装后 upsert。
 */
public interface RptStatDailyMapper {

    /** 重跑安全：同 (tunnel_id, stat_date) 覆盖全部指标列 */
    int upsert(RptStatDailyEntity entity);

    /** 区间查询必须携带数据权限条件（T-704 守护：scope=null 表示全部数据） */
    List<RptStatDailyEntity> selectRange(@Param("tunnelId") Long tunnelId,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to,
                                         @Param("scope") com.tgaws.common.datascope.DataScopeCondition scope);

    List<LocalDate> selectExistingDates(@Param("from") LocalDate from,
                                        @Param("to") LocalDate to);

    // ---------- 聚合源查询（按日窗口） ----------

    /** 预警：total/red(warn_level=4)/confirmed(status 2~5)/closed(status=5) */
    List<Map<String, Object>> warnStatByTunnel(@Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to);

    /** 处置：当日派单 total / 其中已闭环 closed（滚动口径） */
    List<Map<String, Object>> disposeStatByTunnel(@Param("from") LocalDateTime from,
                                                  @Param("to") LocalDateTime to);

    /** 巡检：当日任务 total / 已完成 done */
    List<Map<String, Object>> patrolStatByTunnel(@Param("from") LocalDateTime from,
                                                 @Param("to") LocalDateTime to);

    /** 隐患：当日登记 new / 当日闭环 closed */
    List<Map<String, Object>> hazardStatByTunnel(@Param("from") LocalDateTime from,
                                                 @Param("to") LocalDateTime to);

    /** 样本：逐点位行数（主键扫描当日分区） */
    List<Map<String, Object>> sampleCountByPoint(@Param("from") LocalDateTime from,
                                                 @Param("to") LocalDateTime to);

    /** 点位：id/隧道/采集频率（期望样本数按频率固化） */
    List<Map<String, Object>> pointFreqByTunnel();
}
