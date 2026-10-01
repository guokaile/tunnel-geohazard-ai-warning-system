package com.tgaws.web.config;

import com.tgaws.business.patrol.manager.PatrolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 巡检任务逾期扫描（《3》3.7.2 状态机：1待巡检 且 plan_time 已过 → 4逾期）。
 */
@Component
public class PatrolOverdueScanner {

    private static final Logger log = LoggerFactory.getLogger(PatrolOverdueScanner.class);

    private final PatrolService patrolService;

    public PatrolOverdueScanner(PatrolService patrolService) {
        this.patrolService = patrolService;
    }

    @Scheduled(fixedDelay = 300_000L, initialDelay = 150_000L)
    public void scan() {
        try {
            int count = patrolService.markOverdue();
            if (count > 0) {
                log.info("巡检任务逾期标记 {} 条", count);
            }
        } catch (Exception e) {
            log.error("巡检逾期扫描失败", e);
        }
    }
}
