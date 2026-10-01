package com.tgaws.business.patrol.mapper;

import com.tgaws.business.patrol.entity.PatrolHazardEntity;
import com.tgaws.business.patrol.entity.PatrolPlanEntity;
import com.tgaws.business.patrol.entity.PatrolRecordEntity;
import com.tgaws.business.patrol.entity.PatrolTaskEntity;
import com.tgaws.business.patrol.entity.PatrolTemplateEntity;
import com.tgaws.business.patrol.entity.PatrolTemplateItemEntity;
import com.tgaws.common.datascope.DataScopeCondition;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 巡检 Mapper（计划/模板/任务/记录/隐患，T-606 核心集）。
 */
public interface PatrolMapper {

    // ---------- 模板（FR-502 版本化：uk_template_version(template_no, version)） ----------
    int insertTemplate(PatrolTemplateEntity entity);

    /** 最新版本每模板一条（历史版本留痕，任务/记录按生成时版本解释） */
    List<PatrolTemplateEntity> selectTemplates();

    PatrolTemplateEntity selectTemplate(@Param("id") long id);

    /** 软删该编号全部版本（被启用计划引用时服务层先行拦截 B0506） */
    int deleteTemplateByNo(@Param("templateNo") String templateNo);

    int countEnabledPlansRefTemplate(@Param("templateNo") String templateNo);

    int insertItem(PatrolTemplateItemEntity entity);

    List<PatrolTemplateItemEntity> selectItemsByTemplate(@Param("templateId") long templateId);

    // ---------- 计划（FR-501 自动派发） ----------
    int insertPlan(PatrolPlanEntity entity);

    PatrolPlanEntity selectPlan(@Param("id") long id);

    /** 列表查询必须携带数据权限条件（T-704 守护：scope=null 表示全部数据） */
    List<PatrolPlanEntity> selectPlans(@Param("tunnelId") Long tunnelId,
                                       @Param("scope") DataScopeCondition scope);

    /** update XML <if> 实现不传=不变更（与 GatewayMapper 同口径） */
    int updatePlan(PatrolPlanEntity entity);

    int deletePlan(@Param("id") long id);

    List<PatrolPlanEntity> selectEnabledPlans();

    // ---------- 任务（uk_plan_time(plan_id, plan_time) 幂等生成，准确率 100% 可复核） ----------
    int insertTask(PatrolTaskEntity entity);

    /** 生成前置检查（ON DUPLICATE 无变更时 Connector/J 返回受影响行=1，无法区分新建/重复，故显式预检） */
    int countTaskByPlanTime(@Param("planId") long planId, @Param("planTime") LocalDateTime planTime);

    PatrolTaskEntity selectTask(@Param("id") long id);

    int updateTaskStatus(@Param("id") long id, @Param("status") int status);

    /** 逾期扫描：1待巡检 且 plan_time 已过 → 4逾期 */
    int updateOverdue(@Param("now") LocalDateTime now);

    int countTaskRecords(@Param("taskId") long taskId);

    int countTemplateItems(@Param("templateId") long templateId);

    List<PatrolTaskEntity> selectTasksByInspector(@Param("inspectorId") long inspectorId,
                                                  @Param("status") Integer status,
                                                  @Param("scope") DataScopeCondition scope);

    List<PatrolTaskEntity> selectTasksPage(@Param("inspectorId") Long inspectorId,
                                           @Param("status") Integer status,
                                           @Param("dateFrom") LocalDateTime dateFrom,
                                           @Param("dateTo") LocalDateTime dateTo,
                                           @Param("scope") DataScopeCondition scope,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    long countTasks(@Param("inspectorId") Long inspectorId,
                    @Param("status") Integer status,
                    @Param("dateFrom") LocalDateTime dateFrom,
                    @Param("dateTo") LocalDateTime dateTo,
                    @Param("scope") DataScopeCondition scope);

    // ---------- 记录（离线补传幂等：uk_client_key + uk_task_item 冲突返回已存在） ----------
    int insertRecord(PatrolRecordEntity entity);

    PatrolRecordEntity selectRecordByClientKey(@Param("clientKey") String clientKey);

    List<PatrolRecordEntity> selectRecordsByTask(@Param("taskId") long taskId);

    // ---------- 隐患（FR-504 登记→处置→闭环） ----------
    int insertHazard(PatrolHazardEntity entity);

    PatrolHazardEntity selectHazard(@Param("id") long id);

    /** 转处置/改派：状态 1/2 → 2（条件更新防并发串态） */
    int updateHazardAssign(@Param("id") long id, @Param("handlerId") long handlerId);

    /** 闭环：状态 1/2 → 3，close_remark 必填（条件更新防重复闭环） */
    int closeHazard(@Param("id") long id, @Param("closeTime") LocalDateTime closeTime,
                    @Param("closeRemark") String closeRemark);

    List<PatrolHazardEntity> selectHazardsPage(@Param("tunnelId") Long tunnelId,
                                               @Param("status") Integer status,
                                               @Param("hazardLevel") Integer hazardLevel,
                                               @Param("scope") DataScopeCondition scope,
                                               @Param("offset") int offset,
                                               @Param("limit") int limit);

    long countHazards(@Param("tunnelId") Long tunnelId,
                      @Param("status") Integer status,
                      @Param("hazardLevel") Integer hazardLevel,
                      @Param("scope") DataScopeCondition scope);
}
