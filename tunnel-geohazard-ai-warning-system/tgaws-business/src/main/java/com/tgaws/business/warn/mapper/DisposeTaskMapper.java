package com.tgaws.business.warn.mapper;

import com.tgaws.business.warn.entity.DisposeTaskEntity;
import com.tgaws.business.warn.entity.DisposeFeedbackEntity;
import com.tgaws.common.datascope.DataScopeCondition;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 处置任务 Mapper（warn_dispose_task：闭环派单）。
 */
public interface DisposeTaskMapper {

    int insert(DisposeTaskEntity entity);

    DisposeTaskEntity selectById(@Param("id") long id);

    /** 条件更新：开始处置（期望 1→2） */
    int updateOnStart(@Param("id") long id);

    /** 条件更新：完成处置（期望 2→3） */
    int updateOnFinish(@Param("id") long id);

    /** 超时扫描：deadline 过期且未完成 → 4 超时未完成 */
    int markOverdue(@Param("now") LocalDateTime now);

    List<DisposeTaskEntity> selectByAssignee(@Param("assigneeId") long assigneeId,
                                             @Param("status") Integer status);

    // ---------- T-708 API-C16 分页（数据权限条件携带） ----------
    List<DisposeTaskEntity> selectPage(@Param("assigneeId") Long assigneeId,
                                       @Param("status") Integer status,
                                       @Param("scope") DataScopeCondition scope,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    long countPage(@Param("assigneeId") Long assigneeId,
                   @Param("status") Integer status,
                   @Param("scope") DataScopeCondition scope);

    // ---------- T-708 API-C18 分次反馈 ----------
    int insertFeedback(DisposeFeedbackEntity entity);

    List<DisposeFeedbackEntity> selectFeedbackByTask(@Param("taskId") long taskId);
}
