package com.tgaws.business.patrol.manager;

import com.tgaws.business.patrol.entity.PatrolHazardEntity;
import com.tgaws.business.patrol.entity.PatrolPlanEntity;
import com.tgaws.business.patrol.entity.PatrolRecordEntity;
import com.tgaws.business.patrol.entity.PatrolTaskEntity;
import com.tgaws.business.patrol.entity.PatrolTemplateEntity;
import com.tgaws.business.patrol.entity.PatrolTemplateItemEntity;
import com.tgaws.business.patrol.mapper.PatrolMapper;
import com.tgaws.business.warn.manager.HazardEventService;
import com.tgaws.common.exception.BizException;
import com.tgaws.common.result.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 巡检服务单测（评审 3.2/3.3/3.4/3.5 + FR-501/502/504 验收口径）：
 * 补传幂等与冲突拒绝 / 模板版本+1 与引用保护 / 任务生成派发 100%（幂等重跑）/
 * 完成校验 / 隐患闭环 / 转灾害留痕。
 */
class PatrolServiceTest {

    private PatrolMapper patrolMapper;
    private HazardEventService hazardEventService;
    private PatrolService service;

    @BeforeEach
    void setUp() {
        patrolMapper = mock(PatrolMapper.class);
        hazardEventService = mock(HazardEventService.class);
        org.mockito.Mockito.doAnswer(inv -> {
            PatrolTaskEntity e = inv.getArgument(0);
            e.setId(400L);
            return 1;
        }).when(patrolMapper).insertTask(any(PatrolTaskEntity.class));
        org.mockito.Mockito.doAnswer(inv -> {
            PatrolRecordEntity e = inv.getArgument(0);
            e.setId(500L);
            return 1;
        }).when(patrolMapper).insertRecord(any(PatrolRecordEntity.class));
        org.mockito.Mockito.doAnswer(inv -> {
            PatrolTemplateEntity e = inv.getArgument(0);
            e.setId(30L);
            return 1;
        }).when(patrolMapper).insertTemplate(any(PatrolTemplateEntity.class));
        org.mockito.Mockito.doAnswer(inv -> {
            PatrolHazardEntity e = inv.getArgument(0);
            e.setId(60L);
            return 1;
        }).when(patrolMapper).insertHazard(any(PatrolHazardEntity.class));
        service = new PatrolService(patrolMapper, hazardEventService);
    }

    private PatrolTaskEntity task(int status) {
        PatrolTaskEntity t = new PatrolTaskEntity();
        t.setId(1L);
        t.setInspectorId(8L);
        t.setTemplateId(10L);
        t.setStatus(status);
        return t;
    }

    private PatrolTemplateEntity template(int version) {
        PatrolTemplateEntity t = new PatrolTemplateEntity();
        t.setId(30L);
        t.setTemplateNo("TPL001");
        t.setTemplateName("标准隧道巡检");
        t.setVersion(version);
        t.setStatus(1);
        return t;
    }

    private PatrolPlanEntity plan(int frequency, String slot) {
        PatrolPlanEntity p = new PatrolPlanEntity();
        p.setId(40L);
        p.setPlanNo("PLN001");
        p.setTunnelId(1L);
        p.setTemplateId(30L);
        p.setInspectorId(8L);
        p.setFrequencyType(frequency);
        p.setTimeSlot(slot);
        p.setEnabled(1);
        return p;
    }

    private PatrolService.ItemFillCmd fill(String clientKey) {
        return new PatrolService.ItemFillCmd(31L, "拱顶", clientKey, "无异常", 1, null);
    }

    // ==================== 填报（评审 3.3 补传幂等与冲突拒绝） ====================

    @Test
    void fillRecordsIdempotentByClientKey() {
        when(patrolMapper.selectTask(1L)).thenReturn(task(1));
        PatrolRecordEntity existing = new PatrolRecordEntity();
        existing.setId(99L);
        existing.setClientKey("ck-1");
        when(patrolMapper.selectRecordByClientKey("ck-1")).thenReturn(existing);
        List<PatrolRecordEntity> result = service.fillRecords(
                1L, null, null, null, List.of(fill("ck-1")), 8L);
        assertEquals(99L, result.get(0).getId(), "离线补传幂等：重复 clientKey 返回已存在记录");
        verify(patrolMapper, never()).insertRecord(any());
    }

    @Test
    void fillRecordsRejectedWhenTaskClosed() {
        when(patrolMapper.selectTask(1L)).thenReturn(task(3));
        assertEquals(ErrorCode.B0501, assertThrows(BizException.class, () ->
                service.fillRecords(1L, null, null, null, List.of(fill("ck-2")), 8L))
                .getErrorCode());
    }

    @Test
    void fillRecordsRejectedForWrongInspector() {
        when(patrolMapper.selectTask(1L)).thenReturn(task(1));
        assertEquals(ErrorCode.B0403, assertThrows(BizException.class, () ->
                service.fillRecords(1L, null, null, null, List.of(fill("ck-3")), 9L))
                .getErrorCode());
    }

    @Test
    void fillRecordsRejectsInvalidResult() {
        when(patrolMapper.selectTask(1L)).thenReturn(task(1));
        assertEquals(ErrorCode.A0002, assertThrows(BizException.class, () ->
                service.fillRecords(1L, null, null, null,
                        List.of(new PatrolService.ItemFillCmd(31L, "拱顶", "ck-4", null, 9, null)), 8L))
                .getErrorCode());
    }

    @Test
    void fillRecordsInsertsBatchWithLocation() {
        when(patrolMapper.selectTask(1L)).thenReturn(task(1));
        List<PatrolRecordEntity> result = service.fillRecords(1L, "[\"a.jpg\"]",
                new BigDecimal("120.123456"), new BigDecimal("30.654321"),
                List.of(fill("ck-5"), fill("ck-6")), 8L);
        assertEquals(2, result.size());
        assertEquals(new BigDecimal("120.123456"), result.get(0).getLongitude(), "定位随记录落库（FR-104）");
        verify(patrolMapper, org.mockito.Mockito.times(2)).insertRecord(any(PatrolRecordEntity.class));
    }

    // ==================== 完成校验（评审 3.4 快照口径） ====================

    @Test
    void finishRequiresAllItemsFilled() {
        when(patrolMapper.selectTask(1L)).thenReturn(task(2));
        when(patrolMapper.countTaskRecords(1L)).thenReturn(3);
        when(patrolMapper.countTemplateItems(10L)).thenReturn(5);
        assertEquals(ErrorCode.B0503, assertThrows(BizException.class, () ->
                service.finishTask(1L, 8L)).getErrorCode(), "巡检项未填报完毕不得完成");
    }

    @Test
    void finishSucceedsWhenAllFilled() {
        when(patrolMapper.selectTask(1L)).thenReturn(task(2));
        when(patrolMapper.countTaskRecords(1L)).thenReturn(5);
        when(patrolMapper.countTemplateItems(10L)).thenReturn(5);
        service.finishTask(1L, 8L);
        verify(patrolMapper).updateTaskStatus(1L, 3);
    }

    // ==================== 模板版本化（FR-502 + 评审 3.4） ====================

    @Test
    void createTemplateRequiresItems() {
        assertEquals(ErrorCode.A0002, assertThrows(BizException.class, () ->
                service.createTemplate("模板", null, List.of())).getErrorCode(),
                "模板必须包含至少一个巡检项");
    }

    @Test
    void createTemplateInsertsVersion1WithItems() {
        long id = service.createTemplate("标准隧道巡检", "备注",
                List.of(new PatrolService.ItemCmd("拱顶", "检查拱顶", "无异常", 0)));
        assertEquals(30L, id);
        verify(patrolMapper).insertItem(any(PatrolTemplateItemEntity.class));
    }

    @Test
    void updateTemplateCreatesNewVersionAndCopiesItems() {
        when(patrolMapper.selectTemplate(30L)).thenReturn(template(1));
        when(patrolMapper.selectItemsByTemplate(30L)).thenReturn(List.of(
                item(30L, "拱顶"), item(30L, "涌水")));
        List<PatrolTemplateEntity> captured = new ArrayList<>();
        org.mockito.Mockito.doAnswer(inv -> {
            PatrolTemplateEntity e = inv.getArgument(0);
            captured.add(e);
            e.setId(31L);
            return 1;
        }).when(patrolMapper).insertTemplate(any(PatrolTemplateEntity.class));
        long newId = service.updateTemplate(30L, "标准隧道巡检v2", "改版", null);
        assertEquals(31L, newId);
        assertEquals(2, captured.get(0).getVersion(), "修改=版本+1 新行");
        assertEquals("TPL001", captured.get(0).getTemplateNo(), "同模板号跨版本留痕");
        verify(patrolMapper, org.mockito.Mockito.times(2)).insertItem(any(PatrolTemplateItemEntity.class));
    }

    @Test
    void deleteTemplateBlockedWhenReferencedByEnabledPlan() {
        when(patrolMapper.selectTemplate(30L)).thenReturn(template(1));
        when(patrolMapper.countEnabledPlansRefTemplate("TPL001")).thenReturn(2);
        assertEquals(ErrorCode.B0506, assertThrows(BizException.class, () ->
                service.deleteTemplate(30L)).getErrorCode(), "被启用计划引用的模板禁止删除");
        verify(patrolMapper, never()).deleteTemplateByNo(anyString());
    }

    @Test
    void deleteTemplateSoftDeletesAllVersions() {
        when(patrolMapper.selectTemplate(30L)).thenReturn(template(3));
        when(patrolMapper.countEnabledPlansRefTemplate("TPL001")).thenReturn(0);
        service.deleteTemplate(30L);
        verify(patrolMapper).deleteTemplateByNo("TPL001");
    }

    // ==================== 计划（FR-501） ====================

    @Test
    void createPlanRejectsInvalidFrequency() {
        assertEquals(ErrorCode.A0002, assertThrows(BizException.class, () ->
                service.createPlan("计划", 1L, 9, "08:00", 30L, 8L)).getErrorCode());
    }

    @Test
    void createPlanRejectsBadSlotForDaily() {
        when(patrolMapper.selectTemplate(30L)).thenReturn(template(1));
        assertEquals(ErrorCode.A0002, assertThrows(BizException.class, () ->
                service.createPlan("计划", 1L, 2, "早班", 30L, 8L)).getErrorCode(),
                "每日/每周计划的班次必须为 HH:mm 格式");
    }

    // ==================== 任务生成（FR-501 派发准确率 100%） ====================

    @Test
    void generateShiftPlanCreatesThreeTasksPerShift() {
        when(patrolMapper.selectEnabledPlans()).thenReturn(List.of(plan(1, "三班倒")));
        LocalDate date = LocalDate.of(2026, 9, 28);
        PatrolService.GenerateResult result = service.generateTasksForDate(date);
        assertEquals(3, result.created(), "每班计划生成 00:00/08:00/16:00 三班任务");
        assertEquals(0, result.skipped());
        verify(patrolMapper, org.mockito.Mockito.times(3)).insertTask(any(PatrolTaskEntity.class));
    }

    @Test
    void generateDailyAndWeeklyPlansPerSlot() {
        when(patrolMapper.selectEnabledPlans()).thenReturn(List.of(
                plan(2, "08:00"), plan(3, "09:30")));
        LocalDate monday = LocalDate.of(2026, 9, 28); // 周一
        PatrolService.GenerateResult result = service.generateTasksForDate(monday);
        assertEquals(2, result.created(), "每日 1 班 + 每周（周一）1 班");
    }

    @Test
    void weeklyPlanSkipsNonMonday() {
        when(patrolMapper.selectEnabledPlans()).thenReturn(List.of(plan(3, "09:30")));
        LocalDate tuesday = LocalDate.of(2026, 9, 29);
        PatrolService.GenerateResult result = service.generateTasksForDate(tuesday);
        assertEquals(DayOfWeek.TUESDAY, tuesday.getDayOfWeek());
        assertEquals(0, result.created(), "每周计划仅周一生成");
        verify(patrolMapper, never()).insertTask(any());
    }

    @Test
    void generateIsIdempotentOnDuplicatePlanTime() {
        when(patrolMapper.selectEnabledPlans()).thenReturn(List.of(plan(2, "08:00")));
        // 预检命中：uk_plan_time 已存在（ON DUPLICATE 无变更时 Connector/J 返回受影响行=1，
        // 不能靠 insert 返回值区分，生成逻辑显式预检）
        when(patrolMapper.countTaskByPlanTime(anyLong(), any(LocalDateTime.class))).thenReturn(1);
        PatrolService.GenerateResult result =
                service.generateTasksForDate(LocalDate.of(2026, 9, 29));
        assertEquals(0, result.created());
        assertEquals(1, result.skipped(), "重复生成零副作用（幂等）");
        verify(patrolMapper, never()).insertTask(any());
    }

    @Test
    void markOverdueDelegatesToMapper() {
        when(patrolMapper.updateOverdue(any(LocalDateTime.class))).thenReturn(2);
        assertEquals(2, service.markOverdue());
    }

    // ==================== 隐患（FR-504 闭环 + 评审 3.5） ====================

    @Test
    void registerHazardValidatesSourceAndLevel() {
        assertEquals(ErrorCode.A0002, assertThrows(BizException.class, () -> service.registerHazard(
                new PatrolService.HazardCmd(1L, null, 9, "标题", null, 2, null, null, null, null, null, null), 8L))
                .getErrorCode());
        assertEquals(ErrorCode.A0002, assertThrows(BizException.class, () -> service.registerHazard(
                new PatrolService.HazardCmd(1L, null, 1, "标题", null, 5, null, null, null, null, null, null), 8L))
                .getErrorCode());
    }

    @Test
    void assignHazardRejectedWhenClosed() {
        PatrolHazardEntity closed = new PatrolHazardEntity();
        closed.setId(60L);
        closed.setStatus(3);
        when(patrolMapper.selectHazard(60L)).thenReturn(closed);
        assertEquals(ErrorCode.B0507, assertThrows(BizException.class, () ->
                service.assignHazard(60L, 9L)).getErrorCode(), "已闭环隐患不可转处置");
    }

    @Test
    void closeHazardRequiresRemark() {
        PatrolHazardEntity open = new PatrolHazardEntity();
        open.setId(60L);
        open.setStatus(2);
        when(patrolMapper.selectHazard(60L)).thenReturn(open);
        assertEquals(ErrorCode.B0508, assertThrows(BizException.class, () ->
                service.closeHazard(60L, "  ")).getErrorCode(), "闭环必须携带说明");
    }

    @Test
    void closeHazardSucceeds() {
        PatrolHazardEntity open = new PatrolHazardEntity();
        open.setId(60L);
        open.setStatus(2);
        when(patrolMapper.selectHazard(60L)).thenReturn(open);
        when(patrolMapper.closeHazard(eq(60L), any(LocalDateTime.class), eq("已整改完成")))
                .thenReturn(1);
        service.closeHazard(60L, "已整改完成");
        verify(patrolMapper).closeHazard(eq(60L), any(LocalDateTime.class), eq("已整改完成"));
    }

    @Test
    void convertHazardToDisasterRequiresHazardExists() {
        when(patrolMapper.selectHazard(66L)).thenReturn(null);
        assertEquals(ErrorCode.B0505, assertThrows(BizException.class, () ->
                service.convertHazardToDisaster(66L, 1L, null, 3,
                        LocalDateTime.now().minusHours(1), "拱顶坍塌风险", 2, 9L, "张三", List.of()))
                .getErrorCode(), "不存在的隐患不得转灾害登记");
    }

    @Test
    void convertHazardToDisasterLinksBothFlows() {
        PatrolHazardEntity hazard = new PatrolHazardEntity();
        hazard.setId(66L);
        hazard.setStatus(2);
        when(patrolMapper.selectHazard(66L)).thenReturn(hazard);
        when(hazardEventService.register(any(), anyLong(), any(), any()))
                .thenReturn(700L);
        long id = service.convertHazardToDisaster(66L, 1L, null, 3,
                LocalDateTime.now().minusHours(1), "拱顶坍塌风险", 2, 9L, "张三", List.of(5L));
        assertEquals(700L, id);
        verify(hazardEventService).register(any(), eq(9L), eq("张三"), eq(List.of(5L)));
    }

    // ==================== 分页（API-D07） ====================

    @Test
    void tasksPageComputesOffset() {
        when(patrolMapper.selectTasksPage(eq(8L), eq(1), any(), any(), any(), eq(40), eq(20)))
                .thenReturn(List.of(task(1)));
        when(patrolMapper.countTasks(eq(8L), eq(1), any(), any(), any())).thenReturn(41L);
        var page = service.tasksPage(8L, 1, null, null, 3, 20, com.tgaws.common.datascope.DataScope.all());
        assertEquals(41L, page.total());
        assertEquals(1, page.list().size());
        assertNotNull(page.list().get(0).getId());
    }

    private PatrolTemplateItemEntity item(long templateId, String name) {
        PatrolTemplateItemEntity i = new PatrolTemplateItemEntity();
        i.setId(70L);
        i.setTemplateId(templateId);
        i.setItemName(name);
        i.setCheckContent("检查" + name);
        i.setJudgeStandard("无异常");
        i.setSort(0);
        return i;
    }
}
