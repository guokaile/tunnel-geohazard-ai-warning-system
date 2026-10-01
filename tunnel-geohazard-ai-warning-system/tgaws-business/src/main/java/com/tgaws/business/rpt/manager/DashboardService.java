package com.tgaws.business.rpt.manager;

import com.tgaws.business.rpt.entity.RptStatDailyEntity;
import com.tgaws.business.rpt.mapper.RptDashboardMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 领导驾驶舱（FR-802，指标实时刷新 ≤1min）：
 * "今日"指标走实时段有界小查询（当日行数小 + idx_create_time 索引），
 * 周趋势走 rpt_stat_daily 预聚合——实时 GROUP BY 扫千万行（30~180s）不可能达标。
 */
@Service
public class DashboardService {

    private static final ZoneId DB_ZONE = ZoneId.of("Asia/Shanghai");

    private final RptDashboardMapper dashboardMapper;
    private final RptStatDailyService statDailyService;

    public DashboardService(RptDashboardMapper dashboardMapper, RptStatDailyService statDailyService) {
        this.dashboardMapper = dashboardMapper;
        this.statDailyService = statDailyService;
    }

    public DashboardVo dashboard(long tunnelId, com.tgaws.common.datascope.DataScope scope) {
        LocalDate today = RptStatDailyService.today();
        LocalDateTime dayStart = today.atStartOfDay();

        long todayWarn = dashboardMapper.countWarnByRange(tunnelId, dayStart, null);
        long openEvents = dashboardMapper.countOpenEvents(tunnelId);
        long disposeClosedToday = dashboardMapper.countDisposeClosedByRange(tunnelId, dayStart);
        long disposeOpen = dashboardMapper.countDisposeOpen(tunnelId);
        // 今日闭环率口径：今日已闭环 / (今日已闭环 + 当前未闭环)
        double closeRate = (disposeClosedToday + disposeOpen) == 0 ? 1.0
                : disposeClosedToday * 1.0 / (disposeClosedToday + disposeOpen);

        Map<String, Object> gw = dashboardMapper.gatewayStatus();
        long gwTotal = RptStatDailyService.asLong(gw, "total");
        long gwOnline = RptStatDailyService.asLong(gw, "online");
        double onlineRate = gwTotal == 0 ? 1.0 : gwOnline * 1.0 / gwTotal;

        long[] todaySample = statDailyService.todaySamples(tunnelId);
        double sampleRate = todaySample[1] == 0 ? 1.0 : todaySample[0] * 1.0 / todaySample[1];

        List<TopRiskPoint> risks = new ArrayList<>();
        for (Map<String, Object> row : dashboardMapper.topRiskPoints(
                tunnelId, LocalDateTime.now(DB_ZONE).minusHours(24))) {
            risks.add(new TopRiskPoint(String.valueOf(row.get("pointCode")),
                    String.valueOf(row.get("pointName")),
                    RptStatDailyService.asLong(row, "cnt"),
                    (int) RptStatDailyService.asLong(row, "maxLevel")));
        }

        // 周趋势：近 6 个完整日（预聚合）+ 今日实时点
        LocalDate trendFrom = today.minusDays(6);
        statDailyService.ensureAggregated(trendFrom, today.minusDays(1));
        List<TrendPoint> trend = new ArrayList<>();
        for (RptStatDailyEntity row : statDailyService.rangeRows(tunnelId, trendFrom, today.minusDays(1), scope)) {
            trend.add(new TrendPoint(row.getStatDate(), row.getWarnTotal()));
        }
        trend.add(new TrendPoint(today, todayWarn));

        return new DashboardVo(todayWarn, openEvents, round2(closeRate), round2(onlineRate),
                round2(sampleRate), risks, trend);
    }

    private static double round2(double v) {
        return Math.round(v * 10000) / 10000.0;
    }

    /** 驾驶舱出参（API-F01） */
    public record DashboardVo(long todayWarn, long openEvents, double closeRate,
                              double gatewayOnlineRate, double sampleRate,
                              List<TopRiskPoint> highRiskPoints, List<TrendPoint> trend) {
    }

    /** 高风险点位（近24h 橙/红级事件 TOP10） */
    public record TopRiskPoint(String pointCode, String pointName, long eventCount, int maxLevel) {
    }

    /** 周趋势点（近6日预聚合 + 今日实时） */
    public record TrendPoint(LocalDate date, long warnTotal) {
    }
}
