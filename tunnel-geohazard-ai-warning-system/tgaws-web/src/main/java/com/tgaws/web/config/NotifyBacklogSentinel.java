package com.tgaws.web.config;

import com.tgaws.business.warn.notify.NotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 通知积压哨兵（《6》6.3.3：每 1min 检查待投递积压与最早待投递超时——
 * 系统"哑火"的最后一道哨，超阈值告警管理员）。
 */
@Component
public class NotifyBacklogSentinel {

    private static final Logger log = LoggerFactory.getLogger(NotifyBacklogSentinel.class);

    private final NotifyService notifyService;

    public NotifyBacklogSentinel(NotifyService notifyService) {
        this.notifyService = notifyService;
    }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void check() {
        try {
            notifyService.sentinel();
        } catch (Exception e) {
            log.error("通知积压哨兵检查失败（D0007 口径）", e);
        }
    }
}
