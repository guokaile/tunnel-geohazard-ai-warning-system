package com.tgaws.web.controller;

import com.tgaws.business.mon.entity.RuleEntity;
import com.tgaws.business.warn.entity.DisposeFeedbackEntity;
import com.tgaws.business.warn.entity.DisposeTaskEntity;
import com.tgaws.business.warn.entity.NotifyLogEntity;
import com.tgaws.business.warn.entity.TimelineEntity;
import com.tgaws.business.warn.entity.WarnEventEntity;
import com.tgaws.business.warn.manager.DisposeTaskService;
import com.tgaws.business.warn.manager.RuleService;
import com.tgaws.business.warn.manager.WarnEventService;
import com.tgaws.business.warn.notify.NotifyService;
import com.tgaws.common.result.PageResult;
import com.tgaws.common.result.Result;
import com.tgaws.web.log.OperLog;
import com.tgaws.web.security.LoginContext;
import com.tgaws.web.security.RateLimit;
import com.tgaws.web.security.RequirePermission;
import com.tgaws.web.security.ScopeGuard;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 预警中心接口（《4》4.5.2 API-C01~C20，T-708；C21 SSE/C22~C23 灾害登记已单独交付）。
 * 注：C18 图片为路径 JSON 数组，multipart 文件上传待 W8 对象存储接入后启用。
 */
@RestController
@RequestMapping("/api/v1/warn")
public class WarnController {

    private static final int MAX_PAGE_SIZE = 200;

    private final WarnEventService warnEventService;
    private final DisposeTaskService disposeTaskService;
    private final RuleService ruleService;
    private final NotifyService notifyService;

    public WarnController(WarnEventService warnEventService, DisposeTaskService disposeTaskService,
                          RuleService ruleService, NotifyService notifyService) {
        this.warnEventService = warnEventService;
        this.disposeTaskService = disposeTaskService;
        this.ruleService = ruleService;
        this.notifyService = notifyService;
    }

    // ==================== 预警事件（API-C01~C04/C11~C14） ====================

    /** API-C01 预警事件分页（tunnelId/level/status/hazardType/时间/keyword） */
    @GetMapping("/events")
    @RequirePermission("warn:event:view")
    @RateLimit(RateLimit.Tier.QUERY)
    public Result<PageResult<WarnEventEntity>> events(
            @RequestParam(required = false) Long tunnelId,
            @RequestParam(required = false) Integer level,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer hazardType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        if (tunnelId != null) {
            ScopeGuard.requireTunnel(tunnelId);
        }
        pageNum = Math.max(pageNum, 1);
        pageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        return Result.ok(warnEventService.page(tunnelId, level, status, hazardType, keyword,
                from == null ? null : LocalDateTime.parse(from),
                to == null ? null : LocalDateTime.parse(to),
                pageNum, pageSize, LoginContext.getScope()));
    }

    /** API-C02 事件详情（数据出口：越权 C0008） */
    @GetMapping("/events/{id}")
    @RequirePermission("warn:event:view")
    @RateLimit(RateLimit.Tier.QUERY)
    public Result<WarnEventEntity> eventDetail(@PathVariable long id) {
        WarnEventEntity event = warnEventService.getById(id);
        ScopeGuard.requireTunnel(event.getTunnelId());
        return Result.ok(event);
    }

    /** API-C03 事件时间线（FR-406 不可篡改） */
    @GetMapping("/events/{id}/timeline")
    @RequirePermission("warn:event:view")
    @RateLimit(RateLimit.Tier.QUERY)
    public Result<List<TimelineEntity>> timeline(@PathVariable long id) {
        WarnEventEntity event = warnEventService.getById(id);
        ScopeGuard.requireTunnel(event.getTunnelId());
        return Result.ok(warnEventService.timeline(id));
    }

    /** API-C04 预警统计（按隧道×级别×对象） */
    @GetMapping("/events/stats")
    @RequirePermission("warn:event:view")
    @RateLimit(RateLimit.Tier.QUERY)
    public Result<List<Map<String, Object>>> stats(@RequestParam String from, @RequestParam String to) {
        return Result.ok(warnEventService.stats(LocalDateTime.parse(from), LocalDateTime.parse(to),
                LoginContext.getScope()));
    }

    /** API-C11 确认/误报（FR-401：result 1确认/2误报） */
    @PostMapping("/events/{id}/confirm")
    @RequirePermission("warn:event:confirm")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "预警确认", module = "预警中心")
    public Result<Void> confirm(@PathVariable long id, @RequestBody Map<String, Object> body) {
        int result = ((Number) body.getOrDefault("result", 1)).intValue();
        warnEventService.confirm(id, ScopeGuard.userId(), ScopeGuard.username(),
                result == 1, String.valueOf(body.get("conclusion")));
        return Result.ok();
    }

    /** API-C12 人工升级（留痕） */
    @PostMapping("/events/{id}/upgrade")
    @RequirePermission("warn:event:updown")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "预警升级", module = "预警中心")
    public Result<Void> upgrade(@PathVariable long id, @RequestBody Map<String, Object> body) {
        warnEventService.upgrade(id, ScopeGuard.userId(), ScopeGuard.username(),
                ((Number) body.get("newLevel")).intValue(), String.valueOf(body.get("reason")));
        return Result.ok();
    }

    /** API-C13 人工降级（留痕） */
    @PostMapping("/events/{id}/downgrade")
    @RequirePermission("warn:event:updown")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "预警降级", module = "预警中心")
    public Result<Void> downgrade(@PathVariable long id, @RequestBody Map<String, Object> body) {
        warnEventService.downgrade(id, ScopeGuard.userId(), ScopeGuard.username(),
                ((Number) body.get("newLevel")).intValue(), String.valueOf(body.get("reason")));
        return Result.ok();
    }

    /** API-C14 复核消警（FR-404：reason 必填，状态=待复核） */
    @PostMapping("/events/{id}/close")
    @RequirePermission("warn:event:close")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "复核消警", module = "预警中心")
    public Result<Void> close(@PathVariable long id, @RequestBody Map<String, String> body) {
        warnEventService.close(id, ScopeGuard.userId(), ScopeGuard.username(), body.get("reason"));
        return Result.ok();
    }

    // ==================== 处置闭环（API-C15~C19） ====================

    /** API-C15 创建处置任务（FR-402：eventId/assigneeId/measure/deadline） */
    @PostMapping("/tasks")
    @RequirePermission("warn:event:dispatch")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "处置派单", module = "预警中心")
    public Result<Map<String, Long>> dispatch(@RequestBody Map<String, Object> body) {
        long id = disposeTaskService.dispatch(
                ((Number) body.get("eventId")).longValue(),
                ((Number) body.get("assigneeId")).longValue(),
                ScopeGuard.userId(), ScopeGuard.username(),
                String.valueOf(body.get("measure")),
                LocalDateTime.parse(String.valueOf(body.get("deadline"))));
        return Result.ok(Map.of("id", id));
    }

    /** API-C16 处置任务分页（assigneeId 可选=我的待办） */
    @GetMapping("/tasks")
    @RequirePermission("warn:event:view")
    @RateLimit(RateLimit.Tier.QUERY)
    public Result<PageResult<DisposeTaskEntity>> tasks(
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        pageNum = Math.max(pageNum, 1);
        pageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        return Result.ok(disposeTaskService.page(assigneeId, status, pageNum, pageSize,
                LoginContext.getScope()));
    }

    /** API-C17 开始处置（任务责任人；状态 1→2） */
    @PutMapping("/tasks/{id}/start")
    @RequirePermission("warn:event:confirm")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "开始处置", module = "预警中心")
    public Result<Void> startTask(@PathVariable long id) {
        disposeTaskService.start(id, ScopeGuard.userId());
        return Result.ok();
    }

    /** API-C18 处置反馈（FR-403 分次反馈：仅处置中；时间线节点6） */
    @PostMapping("/tasks/{id}/feedback")
    @RequirePermission("warn:event:confirm")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "处置反馈", module = "预警中心")
    public Result<Map<String, Long>> feedback(@PathVariable long id, @RequestBody Map<String, Object> body) {
        long feedbackId = disposeTaskService.feedback(id, ScopeGuard.userId(), ScopeGuard.username(),
                String.valueOf(body.get("content")), body.get("images") == null ? null : String.valueOf(body.get("images")));
        return Result.ok(Map.of("id", feedbackId));
    }

    /** API-C19 完成处置（状态 2→3；事件转待复核） */
    @PutMapping("/tasks/{id}/finish")
    @RequirePermission("warn:event:confirm")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "完成处置", module = "预警中心")
    public Result<Void> finishTask(@PathVariable long id) {
        disposeTaskService.finish(id, ScopeGuard.userId(), ScopeGuard.username());
        return Result.ok();
    }

    // ==================== 规则管理（API-C05~C10） ====================

    @GetMapping("/rules")
    @RequirePermission("warn:rule:view")
    @RateLimit(RateLimit.Tier.QUERY)
    public Result<List<RuleEntity>> rules(@RequestParam(required = false) Integer hazardType,
                                          @RequestParam(required = false) Integer itemType,
                                          @RequestParam(required = false) Integer status) {
        return Result.ok(ruleService.list(hazardType, itemType, status));
    }

    @PostMapping("/rules")
    @RequirePermission("warn:rule:edit")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "新增预警规则", module = "预警中心")
    public Result<Map<String, Long>> createRule(@RequestBody Map<String, Object> body) {
        RuleService.RuleCmd cmd = parseRuleCmd(body);
        return Result.ok(Map.of("id", ruleService.create(cmd)));
    }

    /** API-C07 修改规则（版本+1，历史留痕） */
    @PutMapping("/rules/{id}")
    @RequirePermission("warn:rule:edit")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "修改预警规则", module = "预警中心")
    public Result<Map<String, Long>> updateRule(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return Result.ok(Map.of("id", ruleService.update(id, parseRuleCmd(body))));
    }

    @DeleteMapping("/rules/{id}")
    @RequirePermission("warn:rule:edit")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "删除预警规则", module = "预警中心")
    public Result<Void> deleteRule(@PathVariable long id) {
        ruleService.delete(id);
        return Result.ok();
    }

    @PutMapping("/rules/{id}/status")
    @RequirePermission("warn:rule:edit")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "规则启停", module = "预警中心")
    public Result<Void> ruleStatus(@PathVariable long id, @RequestBody Map<String, Integer> body) {
        ruleService.updateStatus(id, body.getOrDefault("status", 1));
        return Result.ok();
    }

    @GetMapping("/rules/{id}/history")
    @RequirePermission("warn:rule:view")
    @RateLimit(RateLimit.Tier.QUERY)
    public Result<List<RuleEntity>> ruleHistory(@PathVariable long id) {
        return Result.ok(ruleService.history(id));
    }

    // ==================== 通知记录（API-C20） ====================

    /** API-C20 通知记录查询（eventId 必填；出口按事件隧道校验） */
    @GetMapping("/notify-logs")
    @RequirePermission("warn:event:view")
    @RateLimit(RateLimit.Tier.QUERY)
    public Result<PageResult<NotifyLogEntity>> notifyLogs(
            @RequestParam long eventId,
            @RequestParam(required = false) Integer channelType,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        WarnEventEntity event = warnEventService.getById(eventId);
        ScopeGuard.requireTunnel(event.getTunnelId());
        pageNum = Math.max(pageNum, 1);
        pageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        return Result.ok(notifyService.listLogs(eventId, channelType,
                from == null ? null : LocalDateTime.parse(from),
                to == null ? null : LocalDateTime.parse(to), pageNum, pageSize));
    }

    // ==================== 参数解析 ====================

    private RuleService.RuleCmd parseRuleCmd(Map<String, Object> body) {
        return new RuleService.RuleCmd(
                str(body, "ruleName"),
                body.get("hazardType") == null ? null : ((Number) body.get("hazardType")).intValue(),
                body.get("itemType") == null ? null : ((Number) body.get("itemType")).intValue(),
                body.get("stage") == null ? null : ((Number) body.get("stage")).intValue(),
                body.get("sectionId") == null ? null : ((Number) body.get("sectionId")).longValue(),
                body.get("ruleType") == null ? null : ((Number) body.get("ruleType")).intValue(),
                body.get("warnLevel") == null ? null : ((Number) body.get("warnLevel")).intValue(),
                str(body, "expressionJson"),
                body.get("priority") == null ? null : ((Number) body.get("priority")).intValue(),
                str(body, "remark"));
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
