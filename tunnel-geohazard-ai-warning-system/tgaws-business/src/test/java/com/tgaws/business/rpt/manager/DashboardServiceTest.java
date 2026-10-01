package com.tgaws.business.rpt.manager;

import com.tgaws.business.rpt.entity.RptStatDailyEntity;
import com.tgaws.business.rpt.mapper.RptDashboardMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 驾驶舱单测（FR-802：今日实时段 + 预聚合周趋势；比率口径断言）。
 */
class DashboardServiceTest {

    private RptDashboardMapper dashboardMapper;
    private RptStatDailyService statDailyService;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        dashboardMapper = mock(RptDashboardMapper.class);
        statDailyService = mock(RptStatDailyService.class);
        service = new DashboardService(dashboardMapper, statDailyService);
    }

    private RptStatDailyEntity statRow(LocalDate date, int warnTotal) {
        RptStatDailyEntity e = new RptStatDailyEntity();
        e.setTunnelId(1L);
        e.setStatDate(date);
        e.setWarnTotal(warnTotal);
        return e;
    }

    @Test
    void dashboardComposesAllMetrics() {
        LocalDate today = LocalDate.now();
        when(dashboardMapper.countWarnByRange(eq(1L), any(LocalDateTime.class), any()))
                .thenReturn(12L);
        when(dashboardMapper.countOpenEvents(1L)).thenReturn(5L);
        when(dashboardMapper.countDisposeClosedByRange(eq(1L), any(LocalDateTime.class)))
                .thenReturn(3L);
        when(dashboardMapper.countDisposeOpen(1L)).thenReturn(7L);
        when(dashboardMapper.gatewayStatus())
                .thenReturn(Map.of("total", 4L, "online", 3L));
        when(statDailyService.todaySamples(1L)).thenReturn(new long[]{80L, 100L});
        when(dashboardMapper.topRiskPoints(eq(1L), any(LocalDateTime.class))).thenReturn(List.of(
                Map.of("pointCode", "P00010001", "pointName", "T2点位1", "cnt", 4L, "maxLevel", 4L)));
        when(statDailyService.rangeRows(eq(1L), any(LocalDate.class), any(LocalDate.class), any()))
                .thenReturn(List.of(statRow(today.minusDays(2), 9), statRow(today.minusDays(1), 11)));

        DashboardService.DashboardVo vo = service.dashboard(1L, com.tgaws.common.datascope.DataScope.all());

        assertEquals(12L, vo.todayWarn());
        assertEquals(5L, vo.openEvents());
        assertEquals(0.3, vo.closeRate(), "今日闭环率 = 3/(3+7)");
        assertEquals(0.75, vo.gatewayOnlineRate());
        assertEquals(0.8, vo.sampleRate());
        assertEquals(1, vo.highRiskPoints().size());
        assertEquals("P00010001", vo.highRiskPoints().get(0).pointCode());
        assertEquals(4, vo.highRiskPoints().get(0).maxLevel());
        // 周趋势 = 近6个完整日（预聚合）+ 今日实时点
        assertEquals(3, vo.trend().size());
        assertEquals(today, vo.trend().get(2).date());
        assertEquals(12L, vo.trend().get(2).warnTotal(), "今日趋势点取实时段计数");
        verify(statDailyService).ensureAggregated(eq(today.minusDays(6)), eq(today.minusDays(1)));
    }

    @Test
    void dashboardZeroDenominatorsGiveFullRate() {
        when(dashboardMapper.countWarnByRange(eq(1L), any(LocalDateTime.class), any()))
                .thenReturn(0L);
        when(dashboardMapper.countOpenEvents(1L)).thenReturn(0L);
        when(dashboardMapper.countDisposeClosedByRange(eq(1L), any(LocalDateTime.class)))
                .thenReturn(0L);
        when(dashboardMapper.countDisposeOpen(1L)).thenReturn(0L);
        when(dashboardMapper.gatewayStatus()).thenReturn(Map.of("total", 0L, "online", 0L));
        when(statDailyService.todaySamples(1L)).thenReturn(new long[]{0L, 0L});
        when(dashboardMapper.topRiskPoints(eq(1L), any(LocalDateTime.class))).thenReturn(List.of());
        when(statDailyService.rangeRows(eq(1L), any(LocalDate.class), any(LocalDate.class), any()))
                .thenReturn(List.of());

        DashboardService.DashboardVo vo = service.dashboard(1L, com.tgaws.common.datascope.DataScope.all());
        assertEquals(1.0, vo.closeRate(), "无欠账时闭环率计 100%");
        assertEquals(1.0, vo.gatewayOnlineRate());
        assertEquals(1.0, vo.sampleRate());
        assertEquals(1, vo.trend().size(), "至少含今日点");
    }
}
