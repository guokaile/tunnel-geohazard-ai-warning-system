package com.tgaws.web.config;

import com.tgaws.business.patrol.manager.PatrolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 巡检任务定时任务（FR-501 任务自动生成 + 3.7.2 逾期状态机）：
 *
 * <ul>
 *   <li>每日 00:30 生成次日任务（每班三班次/每日按时间点/每周仅周一，
 *       uk_plan_time 幂等，重复调用 created=0）；</li>
 *   <li>每 5 分钟逾期扫描：1待巡检 且 plan_time 已过 → 4逾期。</li>
 * </ul>
 *
 * <p>失败仅告警不阻断（下周期重试），与同包其余调度任务口径一致。</p>
 */
@Component
public class PatrolTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(PatrolTaskScheduler.class);

    private static final ZoneId DB_ZONE = ZoneId.of("Asia/Shanghai");

    private final PatrolService patrolService;

    public PatrolTaskScheduler(PatrolService patrolService) {
        this.patrolService = patrolService;
    }

    /** FR-501：每日 00:30 生成次日巡检任务 */
    @Scheduled(cron = "0 30 0 * * ?", zone = "Asia/Shanghai")
    public void generateTomorrowTasks() {
        try {
            LocalDate tomorrow = LocalDate.now(DB_ZONE).plusDays(1);
            PatrolService.GenerateResult result = patrolService.generateTasksForDate(tomorrow);
            log.info("次日巡检任务生成完成：{}", result);
        } catch (Exception e) {
            log.warn("次日巡检任务生成失败（不阻断，下周期重试）：{}", e.getMessage());
        }
    }

    /** 逾期扫描：1待巡检 且计划时间已过 → 4逾期 */
    @Scheduled(fixedDelay = 300_000L, initialDelay = 120_000L)
    public void markOverdueTasks() {
        try {
            int count = patrolService.markOverdue();
            if (count > 0) {
                log.info("巡检任务逾期标记 {} 条", count);
            }
        } catch (Exception e) {
            log.warn("巡检任务逾期扫描失败：{}", e.getMessage());
        }
    }
}
