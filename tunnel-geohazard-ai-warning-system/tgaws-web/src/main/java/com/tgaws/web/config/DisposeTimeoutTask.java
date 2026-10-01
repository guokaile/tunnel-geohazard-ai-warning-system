package com.tgaws.web.config;

import com.tgaws.business.warn.manager.DisposeTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 处置任务超时扫描（《6》6.3.3：每 5min，deadline 过期且未完成 → 超时未完成）。
 */
@Component
public class DisposeTimeoutTask {

    private static final Logger log = LoggerFactory.getLogger(DisposeTimeoutTask.class);

    private final DisposeTaskService disposeTaskService;

    public DisposeTimeoutTask(DisposeTaskService disposeTaskService) {
        this.disposeTaskService = disposeTaskService;
    }

    @Scheduled(fixedDelay = 300_000L, initialDelay = 120_000L)
    public void scan() {
        try {
            int count = disposeTaskService.markOverdue();
            if (count > 0) {
                log.info("处置任务超时标记 {} 条", count);
            }
        } catch (Exception e) {
            log.error("处置超时扫描失败（D0007 口径）", e);
        }
    }
}
