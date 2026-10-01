package com.tgaws.web.controller;

import com.tgaws.business.mon.manager.OpenPushService;
import com.tgaws.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Open API（T-702，《4》4.4.3/4.4.4）：第三方推送 + 上级平台拉取（框架）。
 * 鉴权：OpenApiSignFilter（HMAC-SHA256 签名/5min 时间窗/nonce 24h/IP 白名单）。
 */
@RestController
@RequestMapping("/open/v1")
public class OpenPushController {

    private final OpenPushService openPushService;

    public OpenPushController(OpenPushService openPushService) {
        this.openPushService = openPushService;
    }

    /** POST /open/v1/data/push（FR-105：瓦斯监控系统等第三方→本系统） */
    @PostMapping("/data/push")
    @SuppressWarnings("unchecked")
    public Result<Map<String, Integer>> push(@RequestBody Map<String, Object> body) {
        String batchNo = String.valueOf(body.getOrDefault("batchNo", ""));
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        OpenPushService.PushResult result = openPushService.push(batchNo, items);
        return Result.ok(Map.of("received", result.received(), "failed", result.failed()));
    }

    /**
     * GET /open/v1/warn/events（FR-804 预留）：上级平台按游标分页拉取预警事件。
     * 本期实现接口框架与文档（签名链路全量生效），数据聚合逻辑二期启用。
     */
    @GetMapping("/warn/events")
    public Result<Map<String, Object>> warnEvents(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Integer level,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "100") int limit) {
        return Result.ok(Map.of(
                "nextCursor", cursor == null ? "" : cursor,
                "list", List.of(),
                "remark", "接口框架（FR-804 预留）：数据聚合逻辑二期启用"));
    }
}
