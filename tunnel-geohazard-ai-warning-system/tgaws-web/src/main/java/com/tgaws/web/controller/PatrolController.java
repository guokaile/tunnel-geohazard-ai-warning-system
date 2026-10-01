package com.tgaws.web.controller;

import com.tgaws.business.patrol.entity.PatrolHazardEntity;
import com.tgaws.business.patrol.entity.PatrolPlanEntity;
import com.tgaws.business.patrol.entity.PatrolRecordEntity;
import com.tgaws.business.patrol.entity.PatrolTaskEntity;
import com.tgaws.business.patrol.entity.PatrolTemplateEntity;
import com.tgaws.business.patrol.manager.PatrolService;
import com.tgaws.common.result.PageResult;
import com.tgaws.common.result.Result;
import com.tgaws.web.security.LoginContext;
import com.tgaws.web.security.RequirePermission;
import com.tgaws.web.security.ScopeGuard;
import com.tgaws.web.log.OperLog;
import com.tgaws.web.security.RateLimit;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 巡检接口（《4》4.5.4 API-D01~D14，T-606；T-701 接入认证上下文：
 * userId/inspectorId 一律取 JWT 登录态，数据出口 ScopeGuard C0008）。
 */
@RestController
@RequestMapping("/api/v1/patrol")
public class PatrolController {

    private static final int MAX_PAGE_SIZE = 200;

    private final PatrolService patrolService;

    public PatrolController(PatrolService patrolService) {
        this.patrolService = patrolService;
    }

    // ==================== 计划（API-D01/D02） ====================

    @GetMapping("/plans")
    @RequirePermission("patrol:plan:view")
    @RateLimit(RateLimit.Tier.QUERY)
    public Result<List<PatrolPlanEntity>> plans(@RequestParam(required = false) Long tunnelId) {
        if (tunnelId != null) {
            ScopeGuard.requireTunnel(tunnelId);
        }
        return Result.ok(patrolService.plans(tunnelId, LoginContext.getScope()));
    }

    @PostMapping("/plans")
    @RequirePermission("patrol:plan:edit")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "创建巡检计划", module = "巡检管理")
    public Result<Map<String, Long>> createPlan(@RequestBody Map<String, Object> body) {
        long tunnelId = num(body, "tunnelId").longValue();
        ScopeGuard.requireTunnel(tunnelId);
        long id = patrolService.createPlan(
                str(body, "planName"), tunnelId,
                num(body, "frequencyType").intValue(), str(body, "timeSlot"),
                num(body, "templateId").longValue(), num(body, "inspectorId").longValue());
        return Result.ok(Map.of("id", id));
    }

    @PutMapping("/plans/{id}")
    @RequirePermission("patrol:plan:edit")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "修改巡检计划", module = "巡检管理")
    public Result<Void> updatePlan(@PathVariable long id, @RequestBody Map<String, Object> body) {
        patrolService.updatePlan(id,
                str(body, "planName"),
                body.get("tunnelId") == null ? null : num(body, "tunnelId").longValue(),
                body.get("frequencyType") == null ? null : num(body, "frequencyType").intValue(),
                str(body, "timeSlot"),
                body.get("templateId") == null ? null : num(body, "templateId").longValue(),
                body.get("inspectorId") == null ? null : num(body, "inspectorId").longValue(),
                body.get("enabled") == null ? null : num(body, "enabled").intValue());
        return Result.ok();
    }

    @DeleteMapping("/plans/{id}")
    @RequirePermission("patrol:plan:edit")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "删除巡检计划", module = "巡检管理")
    public Result<Void> deletePlan(@PathVariable long id) {
        patrolService.deletePlan(id);
        return Result.ok();
    }

    // ==================== 模板（API-D03~D06） ====================

    @GetMapping("/templates")
    @RequirePermission("patrol:template:view")
    @RateLimit(RateLimit.Tier.QUERY)
    public Result<List<PatrolTemplateEntity>> templates() {
        return Result.ok(patrolService.templates());
    }

    @GetMapping("/templates/{id}")
    @RequirePermission("patrol:template:view")
    @RateLimit(RateLimit.Tier.QUERY)
    public Result<Map<String, Object>> templateDetail(@PathVariable long id) {
        PatrolService.TemplateDetail detail = patrolService.templateDetail(id);
        return Result.ok(Map.of("template", detail.template(), "items", detail.items()));
    }

    @PostMapping("/templates")
    @RequirePermission("patrol:template:edit")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "创建巡检模板", module = "巡检管理")
    public Result<Map<String, Long>> createTemplate(@RequestBody Map<String, Object> body) {
        long id = patrolService.createTemplate(str(body, "templateName"), str(body, "remark"),
                parseItems(body.get("items")));
        return Result.ok(Map.of("id", id));
    }

    /** 修改模板（版本+1，历史任务引用旧版本快照，API-D05） */
    @PutMapping("/templates/{id}")
    @RequirePermission("patrol:template:edit")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "修改巡检模板", module = "巡检管理")
    public Result<Map<String, Long>> updateTemplate(@PathVariable long id,
                                                    @RequestBody Map<String, Object> body) {
        Object rawItems = body.get("items");
        long newId = patrolService.updateTemplate(id, str(body, "templateName"), str(body, "remark"),
                rawItems == null ? null : parseItems(rawItems));
        return Result.ok(Map.of("id", newId));
    }

    @DeleteMapping("/templates/{id}")
    @RequirePermission("patrol:template:edit")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "删除巡检模板", module = "巡检管理")
    public Result<Void> deleteTemplate(@PathVariable long id) {
        patrolService.deleteTemplate(id);
        return Result.ok();
    }

    // ==================== 任务（API-D07~D10） ====================

    @GetMapping("/tasks")
    @RequirePermission("patrol:task:view")
    @RateLimit(RateLimit.Tier.QUERY)
    public Result<PageResult<PatrolTaskEntity>> tasks(
            @RequestParam(required = false) Long inspectorId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        if (inspectorId != null) {
            ScopeGuard.requireSelf(inspectorId);
        }
        pageNum = Math.max(pageNum, 1);
        pageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        return Result.ok(patrolService.tasksPage(inspectorId, status,
                dateFrom == null ? null : LocalDateTime.parse(dateFrom),
                dateTo == null ? null : LocalDateTime.parse(dateTo),
                pageNum, pageSize, LoginContext.getScope()));
    }

    /** 开始巡检：巡检人=登录用户（服务层再校验任务责任人 B0403） */
    @PostMapping("/tasks/{id}/start")
    @RequirePermission("patrol:task:fill")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "开始巡检", module = "巡检管理")
    public Result<Void> start(@PathVariable long id) {
        patrolService.startTask(id, ScopeGuard.userId());
        return Result.ok();
    }

    /**
     * 填报巡检记录（API-D09：items[] 批量填报；离线补传幂等 clientKey 每项唯一）。
     * 图片为路径 JSON 数组；multipart 文件上传待 W8 对象存储接入后启用。
     */
    @PostMapping("/tasks/{id}/records")
    @RequirePermission("patrol:task:fill")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "填报巡检记录", module = "巡检管理")
    public Result<List<PatrolRecordEntity>> fill(@PathVariable long id,
                                                 @RequestBody Map<String, Object> body) {
        List<PatrolService.ItemFillCmd> items = parseItemFills(body.get("items"));
        List<PatrolRecordEntity> records = patrolService.fillRecords(id,
                str(body, "images"),
                body.get("longitude") == null ? null : new BigDecimal(String.valueOf(body.get("longitude"))),
                body.get("latitude") == null ? null : new BigDecimal(String.valueOf(body.get("latitude"))),
                items, ScopeGuard.userId());
        return Result.ok(records);
    }

    @PutMapping("/tasks/{id}/finish")
    @RequirePermission("patrol:task:fill")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "完成巡检", module = "巡检管理")
    public Result<Void> finish(@PathVariable long id) {
        patrolService.finishTask(id, ScopeGuard.userId());
        return Result.ok();
    }

    /** 手动生成巡检任务（演示/补漏用；date 缺省=次日，格式 YYYY-MM-DD；uk_plan_time 幂等） */
    @PostMapping("/tasks/generate")
    @RequirePermission("patrol:plan:edit")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "生成巡检任务", module = "巡检管理")
    public Result<Map<String, Object>> generateTasks(@RequestParam(required = false) String date) {
        LocalDate d = date == null
                ? LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1)
                : LocalDate.parse(date);
        PatrolService.GenerateResult r = patrolService.generateTasksForDate(d);
        return Result.ok(Map.of("created", r.created(), "skipped", r.skipped()));
    }

    // ==================== 隐患（API-D11~D14 + 转灾害） ====================

    @GetMapping("/hazards")
    @RequirePermission("patrol:hazard:view")
    @RateLimit(RateLimit.Tier.QUERY)
    public Result<PageResult<PatrolHazardEntity>> hazards(
            @RequestParam(required = false) Long tunnelId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer level,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        if (tunnelId != null) {
            ScopeGuard.requireTunnel(tunnelId);
        }
        pageNum = Math.max(pageNum, 1);
        pageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        return Result.ok(patrolService.hazardsPage(tunnelId, status, level, pageNum, pageSize,
                LoginContext.getScope()));
    }

    @PostMapping("/hazards")
    @RequirePermission("patrol:hazard:edit")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "隐患登记", module = "巡检管理")
    public Result<Map<String, Long>> registerHazard(@RequestBody Map<String, Object> body) {
        Long tunnelId = body.get("tunnelId") == null ? null : num(body, "tunnelId").longValue();
        ScopeGuard.requireTunnel(tunnelId);
        PatrolService.HazardCmd cmd = new PatrolService.HazardCmd(
                tunnelId,
                body.get("sectionId") == null ? null : num(body, "sectionId").longValue(),
                num(body, "source").intValue(),
                str(body, "title"), str(body, "description"),
                num(body, "hazardLevel").intValue(), str(body, "images"),
                body.get("longitude") == null ? null : new BigDecimal(String.valueOf(body.get("longitude"))),
                body.get("latitude") == null ? null : new BigDecimal(String.valueOf(body.get("latitude"))),
                body.get("taskId") == null ? null : num(body, "taskId").longValue(),
                body.get("recordId") == null ? null : num(body, "recordId").longValue(),
                body.get("hazardType") == null ? null : num(body, "hazardType").intValue());
        long id = patrolService.registerHazard(cmd, ScopeGuard.userId());
        return Result.ok(Map.of("id", id));
    }

    /** 转处置/改派（指定 handlerId，状态 1/2→2，API-D13） */
    @PutMapping("/hazards/{id}")
    @RequirePermission("patrol:hazard:edit")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "隐患转处置", module = "巡检管理")
    public Result<Void> assignHazard(@PathVariable long id, @RequestBody Map<String, Object> body) {
        patrolService.assignHazard(id, num(body, "handlerId").longValue());
        return Result.ok();
    }

    /** 隐患闭环（closeRemark 必填，状态 1/2→3，API-D14） */
    @PutMapping("/hazards/{id}/close")
    @RequirePermission("patrol:hazard:edit")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "隐患闭环", module = "巡检管理")
    public Result<Void> closeHazard(@PathVariable long id, @RequestBody Map<String, String> body) {
        patrolService.closeHazard(id, body.get("closeRemark"));
        return Result.ok();
    }

    /** 巡检隐患转灾害登记（评审 3.5） */
    @PostMapping("/hazards/{id}/convert-disaster")
    @RequirePermission("patrol:hazard:edit")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "隐患转灾害登记", module = "巡检管理")
    public Result<Map<String, Long>> convertDisaster(@PathVariable long id,
                                                     @RequestBody Map<String, Object> body) {
        long tunnelId = num(body, "tunnelId").longValue();
        ScopeGuard.requireTunnel(tunnelId);
        long hazardId = patrolService.convertHazardToDisaster(
                id, tunnelId,
                body.get("sectionId") == null ? null : num(body, "sectionId").longValue(),
                num(body, "hazardType").intValue(),
                LocalDateTime.parse(str(body, "eventTime")),
                str(body, "consequence"),
                num(body, "level").intValue(),
                ScopeGuard.userId(), ScopeGuard.username(),
                parseLongList(body.get("relateEventIds")));
        return Result.ok(Map.of("hazardEventId", hazardId));
    }

    // ==================== 参数解析 ====================

    @SuppressWarnings("unchecked")
    private List<PatrolService.ItemCmd> parseItems(Object raw) {
        List<Map<String, Object>> list = (List<Map<String, Object>>) raw;
        List<PatrolService.ItemCmd> items = new ArrayList<>();
        for (Map<String, Object> m : list) {
            items.add(new PatrolService.ItemCmd(
                    str(m, "itemName"), str(m, "checkContent"), str(m, "judgeStandard"),
                    m.get("sort") == null ? null : ((Number) m.get("sort")).intValue()));
        }
        return items;
    }

    @SuppressWarnings("unchecked")
    private List<PatrolService.ItemFillCmd> parseItemFills(Object raw) {
        List<Map<String, Object>> list = (List<Map<String, Object>>) raw;
        List<PatrolService.ItemFillCmd> items = new ArrayList<>();
        for (Map<String, Object> m : list) {
            items.add(new PatrolService.ItemFillCmd(
                    m.get("itemId") == null ? null : ((Number) m.get("itemId")).longValue(),
                    str(m, "itemName"), str(m, "clientKey"), str(m, "judgeStandardSnapshot"),
                    ((Number) m.getOrDefault("result", 1)).intValue(),
                    str(m, "description")));
        }
        return items;
    }

    @SuppressWarnings("unchecked")
    private List<Long> parseLongList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        List<Object> list = (List<Object>) raw;
        List<Long> ids = new ArrayList<>();
        for (Object o : list) {
            ids.add(((Number) o).longValue());
        }
        return ids;
    }

    private static Number num(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null) {
            throw new IllegalArgumentException("缺少必填参数: " + key);
        }
        return (Number) v;
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
