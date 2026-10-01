package com.tgaws.web.controller;

import com.tgaws.business.warn.entity.HazardEventEntity;
import com.tgaws.business.warn.manager.HazardEventService;
import com.tgaws.common.result.Result;
import com.tgaws.web.security.RequirePermission;
import com.tgaws.web.log.OperLog;
import com.tgaws.web.security.RateLimit;
import com.tgaws.web.security.ScopeGuard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 灾害险情登记接口（FR-407，《4》API-C22/C23，T-606；T-701 接入认证上下文）。
 */
@RestController
@RequestMapping("/api/v1/warn/hazard-events")
public class HazardEventController {

    private final HazardEventService hazardEventService;

    public HazardEventController(HazardEventService hazardEventService) {
        this.hazardEventService = hazardEventService;
    }

    /** API-C22 灾害险情列表：tunnelId 必填 + hazardType/时间范围过滤（数据出口 C0008） */
    @GetMapping
    @RequirePermission("warn:event:view")
    @RateLimit(RateLimit.Tier.QUERY)
    public Result<List<HazardEventEntity>> list(
            @RequestParam long tunnelId,
            @RequestParam(required = false) Integer hazardType,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        ScopeGuard.requireTunnel(tunnelId);
        return Result.ok(hazardEventService.list(tunnelId, hazardType,
                from == null ? null : LocalDateTime.parse(from),
                to == null ? null : LocalDateTime.parse(to)));
    }

    /**
     * API-C23 灾害险情登记（模型评估"灾变时刻"真值锚点）。
     * 关联预警事件一对多：relate_event_id 存主关联，全部关联事件反向
     * hazard_event_id + 时间线节点11"灾变确认"。登记人取认证上下文。
     */
    @PostMapping
    @RequirePermission("warn:hazard:edit")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "灾害险情登记", module = "预警中心")
    public Result<Map<String, Long>> register(@RequestBody Map<String, Object> body) {
        Long tunnelId = body.get("tunnelId") == null ? null : ((Number) body.get("tunnelId")).longValue();
        ScopeGuard.requireTunnel(tunnelId);
        HazardEventService.RegisterCmd cmd = new HazardEventService.RegisterCmd(
                tunnelId,
                body.get("sectionId") == null ? null : ((Number) body.get("sectionId")).longValue(),
                body.get("hazardType") == null ? null : ((Number) body.get("hazardType")).intValue(),
                body.get("eventTime") == null ? null : LocalDateTime.parse(String.valueOf(body.get("eventTime"))),
                body.get("position") == null ? null : String.valueOf(body.get("position")),
                body.get("consequence") == null ? null : String.valueOf(body.get("consequence")),
                body.get("level") == null ? null : ((Number) body.get("level")).intValue(),
                body.get("patrolHazardId") == null ? null : ((Number) body.get("patrolHazardId")).longValue(),
                body.get("remark") == null ? null : String.valueOf(body.get("remark")));
        long id = hazardEventService.register(cmd, ScopeGuard.userId(), ScopeGuard.username(),
                parseLongList(body.get("relateEventIds")));
        return Result.ok(Map.of("id", id));
    }

    @SuppressWarnings("unchecked")
    private List<Long> parseLongList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (Object o : (List<Object>) raw) {
            ids.add(((Number) o).longValue());
        }
        return ids;
    }
}
