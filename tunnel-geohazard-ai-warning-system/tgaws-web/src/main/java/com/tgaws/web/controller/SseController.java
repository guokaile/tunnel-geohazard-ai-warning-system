package com.tgaws.web.controller;

import com.tgaws.web.security.RequirePermission;
import com.tgaws.web.sse.SseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 预警事件 SSE 推送（API-C21，评审 4.5：PC 走 SSE、移动 H5 走 5s 轮询——
 * 移动端离线不承诺应用内推送，靠短信覆盖橙/红级，恢复后按 since 时间戳补拉 API-C01）。
 * 认证：EventSource 无法携带自定义头，token 经 ?token= 查询参数（AuthInterceptor 兜底），
 * 前端生成流地址时不得将 token 写入 Referer 可泄露的日志（见《6》6.3.8）。
 */
@RestController
@RequestMapping("/api/v1/warn")
public class SseController {

    private final SseService sseService;

    public SseController(SseService sseService) {
        this.sseService = sseService;
    }

    @GetMapping("/stream")
    @RequirePermission("warn:event:view")
    public SseEmitter stream(@RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return sseService.register(lastEventId);
    }
}
