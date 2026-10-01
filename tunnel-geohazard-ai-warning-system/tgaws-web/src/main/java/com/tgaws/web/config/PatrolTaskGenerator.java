package com.tgaws.web.config;

import com.tgaws.business.patrol.manager.PatrolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 巡检任务生成器（《6》6.4.4：每日 00:30 生成次日任务，FR-501 派发准确率 100%）。
 * uk_plan_time(plan_id, plan_time) 幂等：重复执行/补跑零副作用（created=0）。
 */
@Component
public class PatrolTaskGenerator {

    private static final Logger log = LoggerFactory.getLogger(PatrolTaskGenerator.class);

    private static final ZoneId DB_ZONE = ZoneId.of("Asia/Shanghai");

    private final PatrolService patrolService;

    public PatrolTaskGenerator(PatrolService patrolService) {
        this.patrolService = patrolService;
    }

    @Scheduled(cron = "0 30 0 * * ?", zone = "Asia/Shanghai")
    public void generateNextDay() {
        try {
            LocalDate next = LocalDate.now(DB_ZONE).plusDays(1);
            PatrolService.GenerateResult result = patrolService.generateTasksForDate(next);
            log.info("巡检任务生成完成：{} 新建 {} 跳过 {}（幂等）", next,
                    result.created(), result.skipped());
        } catch (Exception e) {
            log.error("巡检任务生成失败（次日补跑靠 uk_plan_time 幂等兜底）", e);
        }
    }
}
