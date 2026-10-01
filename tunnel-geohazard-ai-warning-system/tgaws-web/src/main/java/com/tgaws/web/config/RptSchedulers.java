package com.tgaws.web.config;

import com.tgaws.business.rpt.manager.RptStatDailyService;
import com.tgaws.business.rpt.manager.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 报表调度与启动补数（T-607）：
 * <ul>
 *   <li>启动补数：近 7 日聚合缺失日补齐（幂等 upsert，重复执行零副作用）；</li>
 *   <li>每日 01:00 聚合昨日（巡逻任务生成 00:30 之后）；</li>
 *   <li>报告自动生成：01:30 日报（昨日）、周一 01:40 周报（上周）、每月 1 日 01:50 月报（上月）。</li>
 * </ul>
 */
@Component
public class RptSchedulers implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RptSchedulers.class);

    private final RptStatDailyService statDailyService;
    private final ReportService reportService;

    public RptSchedulers(RptStatDailyService statDailyService, ReportService reportService) {
        this.statDailyService = statDailyService;
        this.reportService = reportService;
    }

    /** 启动补数：近 7 日（含今天之前的完整日）聚合缺失补齐 */
    @Override
    public void run(ApplicationArguments args) {
        try {
            LocalDate today = RptStatDailyService.today();
            int rows = statDailyService.ensureAggregated(today.minusDays(6), today.minusDays(1));
            if (rows > 0) {
                log.info("报表日聚合启动补数：{} 天", rows);
            }
        } catch (Exception e) {
            log.error("报表日聚合启动补数失败", e);
        }
    }

    @Scheduled(cron = "0 0 1 * * ?", zone = "Asia/Shanghai")
    public void aggregateYesterday() {
        try {
            LocalDate yesterday = RptStatDailyService.today().minusDays(1);
            statDailyService.aggregateForDate(yesterday);
            log.info("报表日聚合定时完成：{}", yesterday);
        } catch (Exception e) {
            log.error("报表日聚合定时任务失败（启动补数幂等兜底）", e);
        }
    }

    @Scheduled(cron = "0 30 1 * * ?", zone = "Asia/Shanghai")
    public void dailyReport() {
        generateSafely(1, RptStatDailyService.today().minusDays(1).toString());
    }

    @Scheduled(cron = "0 40 1 * * MON", zone = "Asia/Shanghai")
    public void weeklyReport() {
        LocalDate lastMonday = RptStatDailyService.today().minusWeeks(1);
        while (lastMonday.getDayOfWeek().getValue() != 1) {
            lastMonday = lastMonday.minusDays(1);
        }
        int week = lastMonday.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
        generateSafely(2, lastMonday.getYear() + String.format("%02d", week));
    }

    @Scheduled(cron = "0 50 1 1 * ?", zone = "Asia/Shanghai")
    public void monthlyReport() {
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        generateSafely(3, lastMonth.toString());
    }

    private void generateSafely(int type, String period) {
        try {
            // 系统任务无登录态：全量数据口径
            reportService.generateReport(type, period, com.tgaws.common.datascope.DataScope.all());
        } catch (Exception e) {
            log.error("分析报告自动生成失败：type={} period={}", type, period, e);
        }
    }
}
