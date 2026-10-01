package com.tgaws.web.controller;

import com.tgaws.business.sys.health.SystemHealthService;
import com.tgaws.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统基础接口（《4》API-S01：健康检查）。
 * 免认证（WebMvcConfig 白名单）——安装向导第 8 步验证、Nginx/WinSW 探活共用；
 * 不返回敏感信息（数据库状态仅 UP/DOWN 布尔化）。
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final SystemHealthService healthService;

    public SystemController(SystemHealthService healthService) {
        this.healthService = healthService;
    }

    /** API-S01 健康检查：应用存活 + 数据库连通（HTTP 200，状态在 body 内） */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        boolean dbOk = healthService.checkDb();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", dbOk ? "UP" : "DEGRADED");
        body.put("db", dbOk ? "UP" : "DOWN");
        body.put("app", "tgaws");
        body.put("time", System.currentTimeMillis());
        return Result.ok(body);
    }
}
