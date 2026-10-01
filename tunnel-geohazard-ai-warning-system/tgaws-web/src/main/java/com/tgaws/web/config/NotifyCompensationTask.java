package com.tgaws.web.config;

import com.tgaws.business.warn.notify.NotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 通知投递补偿任务（《6》6.3.3：每 30s 扫描待投递/失败待补偿记录；
 * 逐条独立更新，无包裹大事务——评审 4.4）。
 */
@Component
public class NotifyCompensationTask {

    private static final Logger log = LoggerFactory.getLogger(NotifyCompensationTask.class);

    private final NotifyService notifyService;

    public NotifyCompensationTask(NotifyService notifyService) {
        this.notifyService = notifyService;
    }

    /** 任务防重入锁（《7》7.4-7：定时任务防重入） */
    private final java.util.concurrent.atomic.AtomicBoolean running =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    @Scheduled(fixedDelay = 30_000L, initialDelay = 30_000L)
    public void compensate() {
        if (!running.compareAndSet(false, true)) {
            log.warn("通知补偿任务重入跳过（上一轮未完成）");
            return;
        }
        try {
            // 领取式补偿（评审 3.3：FOR UPDATE SKIP LOCKED 防重复投递）
            int processed = notifyService.claimAndDispatch(100);
            if (processed > 0) {
                log.info("通知补偿处理 {} 条", processed);
            }
        } catch (Exception e) {
            log.error("通知补偿任务失败（D0007 口径）", e);
        } finally {
            running.set(false);
        }
    }
}
