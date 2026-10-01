package com.tgaws.business.rpt.manager;

import com.tgaws.business.rpt.entity.RptStatDailyEntity;
import com.tgaws.business.rpt.mapper.RptStatDailyMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 报表日聚合单测（FR-801/802 预聚合口径：upsert 重跑安全 / 缺日补齐 / 数据完整率固化）。
 */
class RptStatDailyServiceTest {

    private RptStatDailyMapper statMapper;
    private RptStatDailyService service;

    @BeforeEach
    void setUp() {
        statMapper = mock(RptStatDailyMapper.class);
        service = new RptStatDailyService(statMapper);
    }

    @Test
    void aggregateForDateAssemblesPerTunnel() {
        LocalDate date = LocalDate.of(2026, 9, 27);
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        when(statMapper.warnStatByTunnel(from, to)).thenReturn(List.of(
                Map.of("tunnelId", 1L, "total", 10L, "red", 2L, "confirmed", 8L, "closed", 5L)));
        when(statMapper.disposeStatByTunnel(from, to)).thenReturn(List.of(
                Map.of("tunnelId", 1L, "total", 6L, "closed", 4L)));
        when(statMapper.patrolStatByTunnel(from, to)).thenReturn(List.of(
                Map.of("tunnelId", 1L, "total", 3L, "done", 3L)));
        when(statMapper.hazardStatByTunnel(from, to)).thenReturn(List.of(
                Map.of("tunnelId", 1L, "newCnt", 2L, "closed", 1L)));
        when(statMapper.sampleCountByPoint(from, to)).thenReturn(List.of(
                Map.of("pointId", 101L, "cnt", 2880L)));
        when(statMapper.pointFreqByTunnel()).thenReturn(List.of(
                Map.of("pointId", 101L, "tunnelId", 1L, "freq", 30L)));

        assertEquals(1, service.aggregateForDate(date));
        var captor = org.mockito.ArgumentCaptor.forClass(RptStatDailyEntity.class);
        verify(statMapper).upsert(captor.capture());
        RptStatDailyEntity e = captor.getValue();
        assertEquals(1L, e.getTunnelId());
        assertEquals(date, e.getStatDate());
        assertEquals(10, e.getWarnTotal());
        assertEquals(2, e.getWarnRed());
        assertEquals(8, e.getWarnConfirmed());
        assertEquals(5, e.getWarnClosed());
        assertEquals(6, e.getDisposeTotal());
        assertEquals(4, e.getDisposeClosed());
        assertEquals(3, e.getPatrolTotal());
        assertEquals(3, e.getPatrolDone());
        assertEquals(2, e.getHazardNew());
        assertEquals(1, e.getHazardClosed());
        assertEquals(2880L, e.getSampleCount());
        assertEquals(86400 / 30L, e.getSampleExpect(), "期望样本按采集频率固化（审计 P2-14）");
    }

    @Test
    void aggregateForDateNoDataNoUpsert() {
        LocalDate date = LocalDate.of(2026, 9, 27);
        when(statMapper.warnStatByTunnel(any(), any())).thenReturn(List.of());
        when(statMapper.disposeStatByTunnel(any(), any())).thenReturn(List.of());
        when(statMapper.patrolStatByTunnel(any(), any())).thenReturn(List.of());
        when(statMapper.hazardStatByTunnel(any(), any())).thenReturn(List.of());
        when(statMapper.sampleCountByPoint(any(), any())).thenReturn(List.of());
        when(statMapper.pointFreqByTunnel()).thenReturn(List.of());
        assertEquals(0, service.aggregateForDate(date));
        verify(statMapper, never()).upsert(any());
    }

    @Test
    void ensureAggregatedSkipsExistingDates() {
        LocalDate from = LocalDate.of(2026, 9, 25);
        LocalDate to = LocalDate.of(2026, 9, 27);
        when(statMapper.selectExistingDates(from, to)).thenReturn(List.of(from, from.plusDays(1)));
        when(statMapper.warnStatByTunnel(any(), any())).thenReturn(List.of());
        when(statMapper.disposeStatByTunnel(any(), any())).thenReturn(List.of());
        when(statMapper.patrolStatByTunnel(any(), any())).thenReturn(List.of());
        when(statMapper.hazardStatByTunnel(any(), any())).thenReturn(List.of());
        when(statMapper.sampleCountByPoint(any(), any())).thenReturn(List.of());
        when(statMapper.pointFreqByTunnel()).thenReturn(List.of());
        assertEquals(0, service.ensureAggregated(from, to), "全部已有日期 → 0 聚合（幂等）");
        // 仅缺失的最后一天被聚合（无数据 → 无 upsert，但窗口正确）
        verify(statMapper).sampleCountByPoint(eq(to.atStartOfDay()), eq(to.plusDays(1).atStartOfDay()));
    }

    @Test
    void todaySamplesUsesElapsedExpectation() {
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        when(statMapper.sampleCountByPoint(eq(dayStart), any())).thenReturn(List.of(
                Map.of("pointId", 101L, "cnt", 1500L)));
        when(statMapper.pointFreqByTunnel()).thenReturn(List.of(
                Map.of("pointId", 101L, "tunnelId", 1L, "freq", 30L),
                Map.of("pointId", 102L, "tunnelId", 2L, "freq", 60L)));
        long[] result = service.todaySamples(1L);
        assertEquals(1500L, result[0], "实收=当日样本数");
        long elapsed = java.time.Duration.between(dayStart, LocalDateTime.now()).getSeconds();
        assertEquals(elapsed / 30L, result[1], "期望=按频率×自零点已过秒数（实时段口径）");
        verify(statMapper, never()).upsert(any());
    }

    @Test
    void rangeRowsDelegates() {
        service.rangeRows(1L, LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 27), com.tgaws.common.datascope.DataScope.all());
        verify(statMapper).selectRange(eq(1L), eq(LocalDate.of(2026, 9, 20)), eq(LocalDate.of(2026, 9, 27)), any());
    }

    @Test
    void asLongHandlesNumbersAndNull() {
        assertEquals(5L, RptStatDailyService.asLong(Map.of("a", 5), "a"));
        assertEquals(5L, RptStatDailyService.asLong(Map.of("a", 5.7), "a"));
        assertEquals(0L, RptStatDailyService.asLong(Map.of(), "a"));
    }
}
