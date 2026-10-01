package com.tgaws.business.warn.mapper;

import com.tgaws.business.warn.entity.WarnEventEntity;

import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 预警事件 Mapper（warn_event：去重查询 + 事件生成 + 条件更新状态机——并发安全）。
 */
public interface WarnEventMapper {

    /** 查某点位未消警事件（去重抑制 B0306 判断；状态 1待确认/2已确认/3处置中/4待复核） */
    WarnEventEntity selectActiveByPoint(@Param("pointId") long pointId);

    /** 插入预警事件（gate_stage 影子标记独立字段，评审 3.3） */
    int insert(WarnEventEntity entity);

    /** 条件更新：确认/误报（期望状态=1待确认 → 目标状态） */
    int updateOnConfirm(@Param("id") long id, @Param("warnStatus") int warnStatus,
                        @Param("confirmUserId") Long confirmUserId,
                        @Param("confirmResult") String confirmResult);

    /** 条件更新：复核消警（期望状态=4待复核 → 5已消警；原因必填） */
    int updateOnClose(@Param("id") long id, @Param("closeUserId") Long closeUserId,
                      @Param("closeReason") String closeReason);

    /** 条件升级：仅当新级别更高且事件未消警（1/2/3 态） */
    int updateLevelUp(@Param("id") long id, @Param("newLevel") int newLevel);

    /** 条件降级：仅当新级别更低且事件未消警 */
    int updateLevelDown(@Param("id") long id, @Param("newLevel") int newLevel);

    /** 关联灾害登记（反向一对多：warn_event.hazard_event_id） */
    int updateHazardLink(@Param("id") long id, @Param("hazardEventId") long hazardEventId);

    /** 派单迁移（期望 2/3 → 3 处置中） */
    int updateToDisposing(@Param("id") long id);

    /** 处置完成迁移（期望 3 → 4 待复核） */
    int updateToPendingReview(@Param("id") long id);

    WarnEventEntity selectById(@Param("id") long id);

    // ---------- T-708 API-C01/C04 列表与统计（数据权限条件强制携带） ----------

    List<WarnEventEntity> selectPage(@Param("tunnelId") Long tunnelId,
                                     @Param("level") Integer level,
                                     @Param("status") Integer status,
                                     @Param("hazardType") Integer hazardType,
                                     @Param("keyword") String keyword,
                                     @Param("from") java.time.LocalDateTime from,
                                     @Param("to") java.time.LocalDateTime to,
                                     @Param("scope") com.tgaws.common.datascope.DataScopeCondition scope,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    long countPage(@Param("tunnelId") Long tunnelId,
                   @Param("level") Integer level,
                   @Param("status") Integer status,
                   @Param("hazardType") Integer hazardType,
                   @Param("keyword") String keyword,
                   @Param("from") java.time.LocalDateTime from,
                   @Param("to") java.time.LocalDateTime to,
                   @Param("scope") com.tgaws.common.datascope.DataScopeCondition scope);

    /** API-C04 预警统计：按隧道×级别×对象聚合 */
    List<java.util.Map<String, Object>> stats(@Param("from") java.time.LocalDateTime from,
                                              @Param("to") java.time.LocalDateTime to,
                                              @Param("scope") com.tgaws.common.datascope.DataScopeCondition scope);
}
