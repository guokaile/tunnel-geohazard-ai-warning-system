package com.tgaws.business.patrol.manager;

import com.tgaws.business.patrol.entity.PatrolHazardEntity;
import com.tgaws.business.patrol.entity.PatrolPlanEntity;
import com.tgaws.business.patrol.entity.PatrolRecordEntity;
import com.tgaws.business.patrol.entity.PatrolTaskEntity;
import com.tgaws.business.patrol.entity.PatrolTemplateEntity;
import com.tgaws.business.patrol.entity.PatrolTemplateItemEntity;
import com.tgaws.business.patrol.mapper.PatrolMapper;
import com.tgaws.business.warn.manager.HazardEventService;
import com.tgaws.common.datascope.DataScope;
import com.tgaws.common.datascope.DataScopeCondition;
import com.tgaws.common.datascope.DataScopeHelper;
import com.tgaws.common.exception.BizException;
import com.tgaws.common.result.ErrorCode;
import com.tgaws.common.result.PageResult;
import com.tgaws.common.util.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 巡检服务（FR-501~504 + 评审 3.2/3.3/3.4/3.5）：
 *
 * <ul>
 *   <li><b>任务生成（FR-501）</b>：每日 00:30 由 PatrolTaskGenerator 生成次日任务；
 *       每班→00:00/08:00/16:00 三班，每日→time_slot，每周→周一 time_slot；
 *       uk_plan_time(plan_id, plan_time) + ON DUPLICATE 无操作 → 重复生成零副作用，
 *       派发准确率 100% 可复核（生成数=启用计划×班次数，重跑 created=0）；</li>
 *   <li><b>模板版本化（FR-502）</b>：修改=同 template_no 新版本行（version+1），
 *       历史任务/记录按生成时版本解释（patrol_task.template_id + 记录快照）；</li>
 *   <li><b>离线补传幂等（3.3）</b>：clientKey（前端离线暂存 UUID）uk 唯一，
 *       ON DUPLICATE 无操作返回已存在；任务被改派/取消时补传拒绝（B0501/B0403 语义）；</li>
 *   <li><b>模板快照（3.4）</b>：item_name/judge_standard_snapshot 随记录落库，
 *       模板改版后历史记录按旧标准解释；</li>
 *   <li><b>隐患闭环（FR-504/3.5）</b>：登记→转处置（指定 handler）→闭环（closeRemark 必填）；
 *       达灾害级经"转灾害登记"生成 warn_hazard_event（patrolHazardId 留痕）。</li>
 * </ul>
 */
@Service
public class PatrolService {

    private static final Logger log = LoggerFactory.getLogger(PatrolService.class);

    private static final ZoneId DB_ZONE = ZoneId.of("Asia/Shanghai");

    /** 每班三班次时间点（夜班/早班/中班） */
    private static final LocalTime[] SHIFT_TIMES = {
            LocalTime.of(0, 0), LocalTime.of(8, 0), LocalTime.of(16, 0)};

    private static final int FREQ_SHIFT = 1;
    private static final int FREQ_DAILY = 2;
    private static final int FREQ_WEEKLY = 3;

    private final PatrolMapper patrolMapper;
    private final HazardEventService hazardEventService;
    private final SnowflakeIdGenerator idGenerator = SnowflakeIdGenerator.defaultInstance();

    public PatrolService(PatrolMapper patrolMapper, HazardEventService hazardEventService) {
        this.patrolMapper = patrolMapper;
        this.hazardEventService = hazardEventService;
    }

    // ==================== 模板（FR-502 版本化） ====================

    /** 新增模板（版本 1 + 巡检项） */
    @Transactional
    public long createTemplate(String templateName, String remark, List<ItemCmd> items) {
        if (templateName == null || templateName.isBlank()) {
            throw new BizException(ErrorCode.A0002, "模板名称必填");
        }
        if (items == null || items.isEmpty()) {
            throw new BizException(ErrorCode.A0002, "模板必须包含至少一个巡检项");
        }
        PatrolTemplateEntity entity = new PatrolTemplateEntity();
        entity.setTemplateNo(nextNo("TPL"));
        entity.setTemplateName(templateName);
        entity.setVersion(1);
        entity.setStatus(1);
        entity.setRemark(remark);
        patrolMapper.insertTemplate(entity);
        insertItems(entity.getId(), items);
        log.info("巡检模板创建：{} v1（{} 项）", entity.getTemplateNo(), items.size());
        return entity.getId();
    }

    /** 修改模板=版本+1 新行（历史任务引用旧版本快照，评审 3.4） */
    @Transactional
    public long updateTemplate(long id, String templateName, String remark, List<ItemCmd> items) {
        PatrolTemplateEntity current = requireTemplate(id);
        if (templateName == null || templateName.isBlank()) {
            throw new BizException(ErrorCode.A0002, "模板名称必填");
        }
        List<ItemCmd> effectiveItems = items;
        if (effectiveItems == null || effectiveItems.isEmpty()) {
            // 未传巡检项 → 沿用上一版本（改名/备注式小改）
            effectiveItems = patrolMapper.selectItemsByTemplate(id).stream()
                    .map(i -> new ItemCmd(i.getItemName(), i.getCheckContent(),
                            i.getJudgeStandard(), i.getSort()))
                    .toList();
        }
        PatrolTemplateEntity next = new PatrolTemplateEntity();
        next.setTemplateNo(current.getTemplateNo());
        next.setTemplateName(templateName);
        next.setVersion(current.getVersion() + 1);
        next.setStatus(current.getStatus());
        next.setRemark(remark);
        patrolMapper.insertTemplate(next);
        insertItems(next.getId(), effectiveItems);
        log.info("巡检模板改版：{} v{}→v{}（{} 项）", current.getTemplateNo(),
                current.getVersion(), next.getVersion(), effectiveItems.size());
        return next.getId();
    }

    /** 删除模板：被启用计划引用禁止（B0506） */
    @Transactional
    public void deleteTemplate(long id) {
        PatrolTemplateEntity current = requireTemplate(id);
        if (patrolMapper.countEnabledPlansRefTemplate(current.getTemplateNo()) > 0) {
            throw new BizException(ErrorCode.B0506, "模板 " + current.getTemplateNo()
                    + " 仍被启用计划引用，先停用/调整计划再删除");
        }
        patrolMapper.deleteTemplateByNo(current.getTemplateNo());
        log.info("巡检模板删除：{}（全部 {} 个版本）", current.getTemplateNo(), current.getVersion());
    }

    /** 模板列表（每模板最新版本一行） */
    public List<PatrolTemplateEntity> templates() {
        return patrolMapper.selectTemplates();
    }

    /** 模板详情（含巡检项） */
    public TemplateDetail templateDetail(long id) {
        PatrolTemplateEntity entity = requireTemplate(id);
        return new TemplateDetail(entity, patrolMapper.selectItemsByTemplate(id));
    }

    private void insertItems(long templateId, List<ItemCmd> items) {
        int sort = 0;
        for (ItemCmd item : items) {
            if (item.itemName() == null || item.itemName().isBlank()
                    || item.checkContent() == null || item.checkContent().isBlank()) {
                throw new BizException(ErrorCode.A0002, "巡检项名称与检查内容必填");
            }
            PatrolTemplateItemEntity e = new PatrolTemplateItemEntity();
            e.setTemplateId(templateId);
            e.setItemName(item.itemName());
            e.setCheckContent(item.checkContent());
            e.setJudgeStandard(item.judgeStandard());
            e.setSort(item.sort() != null ? item.sort() : sort);
            patrolMapper.insertItem(e);
            sort++;
        }
    }

    // ==================== 计划（FR-501） ====================

    @Transactional
    public long createPlan(String planName, long tunnelId, int frequencyType, String timeSlot,
                           long templateId, long inspectorId) {
        if (planName == null || planName.isBlank()) {
            throw new BizException(ErrorCode.A0002, "计划名称必填");
        }
        validateFrequencySlot(frequencyType, timeSlot);
        requireTemplate(templateId);
        PatrolPlanEntity entity = new PatrolPlanEntity();
        entity.setPlanNo(nextNo("PLN"));
        entity.setPlanName(planName);
        entity.setTunnelId(tunnelId);
        entity.setFrequencyType(frequencyType);
        entity.setTimeSlot(timeSlot);
        entity.setTemplateId(templateId);
        entity.setInspectorId(inspectorId);
        entity.setEnabled(1);
        patrolMapper.insertPlan(entity);
        log.info("巡检计划创建：{}（频率 {} slot {} 模板 {}）",
                entity.getPlanNo(), frequencyType, timeSlot, templateId);
        return entity.getId();
    }

    /** 计划修改（不传=不变更；频率与班次联合校验按生效值） */
    @Transactional
    public void updatePlan(long id, String planName, Long tunnelId, Integer frequencyType,
                           String timeSlot, Long templateId, Long inspectorId, Integer enabled) {
        PatrolPlanEntity current = requirePlan(id);
        int effFreq = frequencyType != null ? frequencyType : current.getFrequencyType();
        String effSlot = timeSlot != null ? timeSlot : current.getTimeSlot();
        validateFrequencySlot(effFreq, effSlot);
        if (templateId != null) {
            requireTemplate(templateId);
        }
        PatrolPlanEntity update = new PatrolPlanEntity();
        update.setId(id);
        update.setPlanName(planName);
        update.setTunnelId(tunnelId);
        update.setFrequencyType(frequencyType);
        update.setTimeSlot(timeSlot);
        update.setTemplateId(templateId);
        update.setInspectorId(inspectorId);
        update.setEnabled(enabled);
        patrolMapper.updatePlan(update);
    }

    @Transactional
    public void deletePlan(long id) {
        requirePlan(id);
        patrolMapper.deletePlan(id);
    }

    public List<PatrolPlanEntity> plans(Long tunnelId, DataScope scope) {
        return patrolMapper.selectPlans(tunnelId, DataScopeHelper.forTunnel(scope));
    }

    private void validateFrequencySlot(int frequencyType, String timeSlot) {
        if (frequencyType < FREQ_SHIFT || frequencyType > FREQ_WEEKLY) {
            throw new BizException(ErrorCode.A0002, "频率类型必须为 1每班/2每日/3每周");
        }
        if (timeSlot == null || timeSlot.isBlank()) {
            throw new BizException(ErrorCode.A0002, "班次/时间点必填");
        }
        if (frequencyType != FREQ_SHIFT) {
            try {
                LocalTime.parse(timeSlot, DateTimeFormatter.ofPattern("HH:mm"));
            } catch (DateTimeParseException e) {
                throw new BizException(ErrorCode.A0002, "每日/每周计划的班次必须为 HH:mm 格式");
            }
        }
    }

    // ==================== 任务生成与逾期（FR-501 / 3.7.2 状态机） ====================

    /**
     * 按日期批量生成巡检任务（每日 00:30 生成次日）。
     * uk_plan_time 幂等：重复调用 created=0，派发准确率可复核。
     */
    @Transactional
    public GenerateResult generateTasksForDate(LocalDate date) {
        int created = 0;
        int skipped = 0;
        for (PatrolPlanEntity plan : patrolMapper.selectEnabledPlans()) {
            if (plan.getFrequencyType() == FREQ_WEEKLY
                    && date.getDayOfWeek() != DayOfWeek.MONDAY) {
                continue; // 每周计划仅周一生成
            }
            for (LocalTime slot : slotsFor(plan)) {
                // 显式预检：ON DUPLICATE 无变更时 Connector/J 受影响行=1，不能靠 insert 返回值区分
                if (patrolMapper.countTaskByPlanTime(plan.getId(), date.atTime(slot)) > 0) {
                    skipped++; // uk_plan_time 命中：已生成过
                    continue;
                }
                PatrolTaskEntity task = new PatrolTaskEntity();
                task.setTaskNo(nextNo("XJ"));
                task.setPlanId(plan.getId());
                task.setTunnelId(plan.getTunnelId());
                task.setInspectorId(plan.getInspectorId());
                task.setTemplateId(plan.getTemplateId());
                task.setPlanTime(date.atTime(slot));
                task.setStatus(1);
                patrolMapper.insertTask(task);
                created++;
            }
        }
        log.info("巡检任务生成 {}：新建 {} 跳过 {}", date, created, skipped);
        return new GenerateResult(created, skipped);
    }

    /** 计划 → 班次时间点（每班三班次；每周仅周一） */
    private List<LocalTime> slotsFor(PatrolPlanEntity plan) {
        List<LocalTime> slots = new ArrayList<>();
        if (plan.getFrequencyType() == FREQ_SHIFT) {
            slots.addAll(List.of(SHIFT_TIMES));
        } else if (plan.getFrequencyType() == FREQ_DAILY) {
            slots.add(LocalTime.parse(plan.getTimeSlot(), DateTimeFormatter.ofPattern("HH:mm")));
        } else { // 每周：周一过滤在 generateTasksForDate 按 date 判断
            slots.add(LocalTime.parse(plan.getTimeSlot(), DateTimeFormatter.ofPattern("HH:mm")));
        }
        return slots;
    }

    /** 逾期扫描（1待巡检 且 plan_time 已过 → 4逾期） */
    public int markOverdue() {
        int count = patrolMapper.updateOverdue(LocalDateTime.now(DB_ZONE));
        if (count > 0) {
            log.info("巡检任务逾期标记 {} 条", count);
        }
        return count;
    }

    // ==================== 任务执行 ====================

    /** 开始巡检（1→2） */
    @Transactional
    public void startTask(long taskId, long inspectorId) {
        PatrolTaskEntity task = requireTask(taskId);
        if (task.getInspectorId() != inspectorId) {
            throw new BizException(ErrorCode.B0403);
        }
        patrolMapper.updateTaskStatus(taskId, 2);
    }

    /**
     * 批量填报巡检记录（离线补传幂等：clientKey 唯一，重复补传返回已存在记录）。
     * 任务被改派/取消时拒绝（评审 3.3 冲突解决：拒绝并提示）。
     */
    @Transactional
    public List<PatrolRecordEntity> fillRecords(long taskId, String images,
                                                BigDecimal longitude, BigDecimal latitude,
                                                List<ItemFillCmd> items, long recorderId) {
        PatrolTaskEntity task = requireTask(taskId);
        if (task.getStatus() != 1 && task.getStatus() != 2) {
            throw new BizException(ErrorCode.B0501, "巡检任务已关闭或取消，补传被拒绝");
        }
        if (task.getInspectorId() != recorderId) {
            throw new BizException(ErrorCode.B0403);
        }
        if (items == null || items.isEmpty()) {
            throw new BizException(ErrorCode.A0002, "巡检填报项必填");
        }
        List<PatrolRecordEntity> result = new ArrayList<>();
        for (ItemFillCmd item : items) {
            validateResult(item.result());
            if (item.clientKey() != null) {
                PatrolRecordEntity existing = patrolMapper.selectRecordByClientKey(item.clientKey());
                if (existing != null) {
                    log.debug("离线补传幂等命中：clientKey={}", item.clientKey());
                    result.add(existing); // 幂等：重复补传返回已存在
                    continue;
                }
            }
            if (item.itemName() == null || item.itemName().isBlank()) {
                throw new BizException(ErrorCode.A0002, "巡检项名称必填（快照）");
            }
            PatrolRecordEntity record = new PatrolRecordEntity();
            record.setTaskId(taskId);
            record.setClientKey(item.clientKey());
            record.setItemId(item.itemId());
            record.setItemName(item.itemName());
            record.setJudgeStandardSnapshot(item.judgeStandardSnapshot());
            record.setResult(item.result());
            record.setDescription(item.description());
            record.setImages(images);
            record.setLongitude(longitude);
            record.setLatitude(latitude);
            record.setRecordTime(LocalDateTime.now(DB_ZONE));
            record.setRecorderId(recorderId);
            patrolMapper.insertRecord(record);
            result.add(record);
        }
        return result;
    }

    private void validateResult(int result) {
        if (result < 1 || result > 3) {
            throw new BizException(ErrorCode.A0002, "巡检结果必须为 1正常/2异常/3不适用");
        }
    }

    /** 完成巡检（校验全部模板项已填报，评审 3.4 快照口径） */
    @Transactional
    public void finishTask(long taskId, long inspectorId) {
        PatrolTaskEntity task = requireTask(taskId);
        if (task.getInspectorId() != inspectorId) {
            throw new BizException(ErrorCode.B0403);
        }
        int records = patrolMapper.countTaskRecords(taskId);
        int items = patrolMapper.countTemplateItems(task.getTemplateId());
        if (records < items) {
            throw new BizException(ErrorCode.B0503, "巡检项未填报完毕（" + records + "/" + items + "）");
        }
        patrolMapper.updateTaskStatus(taskId, 3);
    }

    /** 巡检任务分页（API-D07：我的待办/逾期/日期范围） */
    public PageResult<PatrolTaskEntity> tasksPage(Long inspectorId, Integer status,
                                                  LocalDateTime dateFrom, LocalDateTime dateTo,
                                                  int pageNum, int pageSize, DataScope scope) {
        DataScopeCondition cond = DataScopeHelper.forTunnel(scope);
        int offset = (pageNum - 1) * pageSize;
        List<PatrolTaskEntity> list = patrolMapper.selectTasksPage(
                inspectorId, status, dateFrom, dateTo, cond, offset, pageSize);
        long total = patrolMapper.countTasks(inspectorId, status, dateFrom, dateTo, cond);
        return new PageResult<>(total, list);
    }

    // ==================== 隐患（FR-504 闭环 + 评审 3.5 转灾害） ====================

    /** 隐患登记（1巡检发现/2人工上报） */
    @Transactional
    public long registerHazard(HazardCmd cmd, long discoverUserId) {
        if (cmd.title() == null || cmd.title().isBlank()) {
            throw new BizException(ErrorCode.A0002, "隐患标题必填");
        }
        if (cmd.source() < 1 || cmd.source() > 2) {
            throw new BizException(ErrorCode.A0002, "隐患来源必须为 1巡检发现/2人工上报");
        }
        if (cmd.hazardLevel() < 1 || cmd.hazardLevel() > 4) {
            throw new BizException(ErrorCode.A0002, "隐患级别必须为 1蓝/2黄/3橙/4红");
        }
        PatrolHazardEntity entity = new PatrolHazardEntity();
        entity.setHazardNo(nextNo("HZ"));
        entity.setTunnelId(cmd.tunnelId());
        entity.setSectionId(cmd.sectionId());
        entity.setSource(cmd.source());
        entity.setTitle(cmd.title());
        entity.setDescription(cmd.description());
        entity.setHazardLevel(cmd.hazardLevel());
        entity.setImages(cmd.images());
        entity.setStatus(1);
        entity.setDiscoverUserId(discoverUserId);
        entity.setLongitude(cmd.longitude());
        entity.setLatitude(cmd.latitude());
        entity.setTaskId(cmd.taskId());
        entity.setRecordId(cmd.recordId());
        entity.setHazardType(cmd.hazardType());
        patrolMapper.insertHazard(entity);
        log.info("隐患登记：{}（级别 {} 来源 {}）", entity.getHazardNo(),
                cmd.hazardLevel(), cmd.source());
        return entity.getId();
    }

    /** 隐患列表（API-D11：tunnelId/status/level 过滤 + 分页） */
    public PageResult<PatrolHazardEntity> hazardsPage(Long tunnelId, Integer status,
                                                      Integer hazardLevel, int pageNum, int pageSize,
                                                      DataScope scope) {
        DataScopeCondition cond = DataScopeHelper.forTunnel(scope);
        int offset = (pageNum - 1) * pageSize;
        List<PatrolHazardEntity> list = patrolMapper.selectHazardsPage(
                tunnelId, status, hazardLevel, cond, offset, pageSize);
        long total = patrolMapper.countHazards(tunnelId, status, hazardLevel, cond);
        return new PageResult<>(total, list);
    }

    /** 转处置/改派（API-D13：指定 handlerId，状态 1/2→2） */
    @Transactional
    public void assignHazard(long id, long handlerId) {
        PatrolHazardEntity hazard = requireHazard(id);
        if (hazard.getStatus() == 3) {
            throw new BizException(ErrorCode.B0507, "隐患已闭环，不可转处置");
        }
        if (patrolMapper.updateHazardAssign(id, handlerId) == 0) {
            throw new BizException(ErrorCode.B0507); // 并发变更兜底（条件更新）
        }
    }

    /** 隐患闭环（API-D14：closeRemark 必填，状态 1/2→3） */
    @Transactional
    public void closeHazard(long id, String closeRemark) {
        requireHazard(id);
        if (closeRemark == null || closeRemark.isBlank()) {
            throw new BizException(ErrorCode.B0508);
        }
        if (patrolMapper.closeHazard(id, LocalDateTime.now(DB_ZONE), closeRemark) == 0) {
            throw new BizException(ErrorCode.B0507); // 已闭环或并发变更（条件更新）
        }
        log.info("隐患闭环：{}", id);
    }

    /** 巡检隐患达灾害级 → 转灾害登记（评审 3.5：两条流程连通，FR-407 真值样本不遗漏） */
    @Transactional
    public long convertHazardToDisaster(long patrolHazardId, long tunnelId, Long sectionId,
                                        int hazardType, LocalDateTime eventTime,
                                        String consequence, int level,
                                        long userId, String userName, List<Long> relateEventIds) {
        requireHazard(patrolHazardId);
        HazardEventService.RegisterCmd cmd = new HazardEventService.RegisterCmd(
                tunnelId, sectionId, hazardType, eventTime, null, consequence, level,
                patrolHazardId, "源自巡检隐患 #" + patrolHazardId);
        return hazardEventService.register(cmd, userId, userName, relateEventIds);
    }

    // ==================== 内部工具 ====================

    private PatrolTaskEntity requireTask(long taskId) {
        PatrolTaskEntity task = patrolMapper.selectTask(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.B0501);
        }
        return task;
    }

    private PatrolTemplateEntity requireTemplate(long id) {
        PatrolTemplateEntity template = patrolMapper.selectTemplate(id);
        if (template == null) {
            throw new BizException(ErrorCode.B0502);
        }
        return template;
    }

    private PatrolPlanEntity requirePlan(long id) {
        PatrolPlanEntity plan = patrolMapper.selectPlan(id);
        if (plan == null) {
            throw new BizException(ErrorCode.B0504);
        }
        return plan;
    }

    private PatrolHazardEntity requireHazard(long id) {
        PatrolHazardEntity hazard = patrolMapper.selectHazard(id);
        if (hazard == null) {
            throw new BizException(ErrorCode.B0505);
        }
        return hazard;
    }

    private String nextNo(String prefix) {
        return prefix + LocalDate.now(DB_ZONE).format(DateTimeFormatter.ofPattern("yyMMdd"))
                + String.format("%06d", idGenerator.nextId() % 1_000_000L);
    }

    // ==================== 命令与结果类型 ====================

    /** 巡检项命令（模板项：sort 可空按传入顺序） */
    public record ItemCmd(String itemName, String checkContent, String judgeStandard, Integer sort) {
    }

    /** 填报命令（clientKey 幂等键；itemName/judgeStandardSnapshot 模板快照） */
    public record ItemFillCmd(Long itemId, String itemName, String clientKey,
                              String judgeStandardSnapshot, int result, String description) {
    }

    /** 隐患登记命令（taskId/recordId 追溯来源，评审 3.5） */
    public record HazardCmd(Long tunnelId, Long sectionId, int source, String title,
                            String description, int hazardLevel, String images,
                            BigDecimal longitude, BigDecimal latitude,
                            Long taskId, Long recordId, Integer hazardType) {
    }

    /** 模板详情（模板 + 巡检项） */
    public record TemplateDetail(PatrolTemplateEntity template,
                                 List<PatrolTemplateItemEntity> items) {
    }

    /** 任务生成结果（created=新建，skipped=uk_plan_time 幂等命中） */
    public record GenerateResult(int created, int skipped) {
    }
}
