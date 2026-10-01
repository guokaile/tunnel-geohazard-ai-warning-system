package com.tgaws.business.data.manager;

import com.tgaws.business.data.mapper.PartitionMaintenanceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分区维护单测（T-709：建分区幂等/过期删除留痕/pmax 巡检告警）。
 */
class PartitionMaintenanceServiceTest {

    private PartitionMaintenanceMapper partitionMapper;
    private PartitionMaintenanceService service;

    @BeforeEach
    void setUp() {
        partitionMapper = mock(PartitionMaintenanceMapper.class);
        service = new PartitionMaintenanceService(partitionMapper);
    }

    private Map<String, Object> part(String name, String boundary) {
        return Map.of("name", name, "boundary", boundary, "rowCount", 0L);
    }

    @Test
    void ensureFuturePartitionsCreatesMissingOnly() {
        YearMonth next = YearMonth.now().plusMonths(1);
        YearMonth next2 = YearMonth.now().plusMonths(2);
        YearMonth next3 = YearMonth.now().plusMonths(3);
        String p1 = "p" + next.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String p2 = "p" + next2.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String p3 = "p" + next3.format(DateTimeFormatter.ofPattern("yyyyMM"));
        // p1 已存在 → 跳过；p2/p3 缺失 → 创建（两表各 2 个）
        when(partitionMapper.partitionsOf(anyString())).thenReturn(List.of(part(p1, "x"), part("pmax", "MAXVALUE")));

        int created = service.ensureFuturePartitions();
        assertEquals(4, created, "两表 × 缺失的 2 个未来分区");
        verify(partitionMapper, never()).reorganizePmax(eq("data_sample"), eq(p1), anyString());
        verify(partitionMapper).reorganizePmax(eq("data_sample"), eq(p2), anyString());
        verify(partitionMapper).reorganizePmax(eq("data_sample_minute"), eq(p3), anyString());
        verify(partitionMapper, times(4)).insertArchiveLog(anyString(), anyString(), eq(1),
                any(), any(), eq(1), anyString());
    }

    @Test
    void dropExpiredDropsOnlyBeforeRetainBoundary() {
        String old = "p202507";   // 边界 2025-08-01 < 1 年前（2025-09-01）→ 删除
        String recent = "p202609"; // 边界 2026-10-01 ≥ 保留线 → 保留
        when(partitionMapper.partitionsOf(anyString())).thenReturn(List.of(
                part(old, "2025-08-01"), part(recent, "2026-10-01"), part("pmax", "MAXVALUE")));
        when(partitionMapper.partitionRowCount(anyString(), eq(old))).thenReturn(123L);

        int dropped = service.dropExpiredPartitions();
        assertEquals(2, dropped, "两表各删 1 个过期分区");
        verify(partitionMapper, times(2)).dropPartition(anyString(), eq(old));
        verify(partitionMapper, never()).dropPartition(anyString(), eq(recent));
        verify(partitionMapper, never()).dropPartition(anyString(), eq("pmax"));
        verify(partitionMapper, times(2)).insertArchiveLog(anyString(), eq(old), eq(2),
                any(), eq(123L), eq(1), anyString());
    }

    @Test
    void dailyCheckHealthyWhenPmaxAndMarginOk() {
        YearMonth far = YearMonth.now().plusMonths(6);
        String boundary = far.plusMonths(1).atDay(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        when(partitionMapper.partitionsOf(anyString())).thenReturn(List.of(
                part("p" + far.format(DateTimeFormatter.ofPattern("yyyyMM")), boundary),
                part("pmax", "MAXVALUE")));
        when(partitionMapper.partitionRowCount(anyString(), eq("pmax"))).thenReturn(0L);
        assertTrue(service.dailyCheck().healthy());
    }

    @Test
    void dailyCheckAlertsOnPmaxDataAndMissingMargin() {
        when(partitionMapper.partitionsOf(anyString())).thenReturn(List.of(
                part("p202609", "2026-10-01"), part("pmax", "MAXVALUE")));
        when(partitionMapper.partitionRowCount(anyString(), eq("pmax"))).thenReturn(5L);
        PartitionMaintenanceService.CheckResult result = service.dailyCheck();
        assertFalse(result.healthy());
        assertTrue(result.problems().contains("pmax 有数据 5 行"));
        assertTrue(result.problems().contains("+90 天"), "边界余量不足告警");
    }
}
