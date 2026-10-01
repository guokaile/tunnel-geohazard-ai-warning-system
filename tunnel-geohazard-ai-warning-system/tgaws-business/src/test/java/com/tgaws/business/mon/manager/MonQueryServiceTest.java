package com.tgaws.business.mon.manager;

import com.tgaws.business.mon.entity.SectionEntity;
import com.tgaws.business.mon.mapper.MonQueryMapper;
import com.tgaws.business.mon.vo.MonQueryVos.OverviewVo;
import com.tgaws.business.mon.vo.MonQueryVos.PointStatsVo;
import com.tgaws.business.mon.vo.MonQueryVos.SeriesPointVo;
import com.tgaws.common.datascope.DataScope;
import com.tgaws.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 监控域查询服务单测（T-811：raw 窗口上限/速率计算/无样本兜底/断面越权/在线率口径）。
 */
class MonQueryServiceTest {

    private MonQueryMapper mapper;
    private MonQueryService service;

    @BeforeEach
    void setUp() {
        mapper = mock(MonQueryMapper.class);
        service = new MonQueryService(mapper);
    }

    @Test
    void seriesRawOver7DaysRejected() {
        assertThrows(BizException.class, () -> service.series(1L,
                LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 9, 10, 0, 0), "raw"));
        verify(mapper, never()).selectSeriesRaw(anyLong(), any(), any(), anyInt());
    }

    @Test
    void seriesMinutePassthrough() {
        when(mapper.selectSeriesMinute(anyLong(), any(), any())).thenReturn(List.of());
        assertEquals(List.of(), service.series(1L,
                LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 9, 30, 0, 0), "minute"));
    }

    @Test
    void statsNoSamplesReturnsZeros() {
        when(mapper.selectSeriesAgg(anyLong(), any(), any())).thenReturn(null);
        PointStatsVo vo = service.stats(1L, LocalDateTime.now(), LocalDateTime.now());
        assertEquals(BigDecimal.ZERO, vo.getMax());
        assertEquals(BigDecimal.ZERO, vo.getRate());
    }

    @Test
    void statsRateComputedPerHour() {
        PointStatsVo agg = new PointStatsVo();
        agg.setMax(new BigDecimal("20"));
        agg.setMin(new BigDecimal("10"));
        agg.setAvg(new BigDecimal("15"));
        when(mapper.selectSeriesAgg(anyLong(), any(), any())).thenReturn(agg);
        SeriesPointVo first = new SeriesPointVo();
        first.setTs(LocalDateTime.of(2026, 9, 29, 8, 0, 0));
        first.setValue(new BigDecimal("10"));
        SeriesPointVo last = new SeriesPointVo();
        last.setTs(LocalDateTime.of(2026, 9, 29, 10, 0, 0));
        last.setValue(new BigDecimal("16"));
        when(mapper.selectSeriesFirstLast(anyLong(), any(), any())).thenReturn(List.of(first, last));
        PointStatsVo vo = service.stats(1L, LocalDateTime.now(), LocalDateTime.now());
        // (16-10) / 2h = 3.0000
        assertEquals(0, new BigDecimal("3.0000").compareTo(vo.getRate()));
    }

    @Test
    void boardSectionNotFoundRejected() {
        // 范围过滤在 SQL 层完成：scope={4} 时断面 99（隧道 9）不会出现在列表 → B0115
        SectionEntity other = new SectionEntity();
        other.setId(99L);
        other.setTunnelId(9L);
        when(mapper.selectSections(any(), any())).thenReturn(List.of());
        DataScope scope = DataScope.ofTunnels(Set.of(4L));
        assertThrows(BizException.class, () -> service.board(99L, scope));
    }

    @Test
    void boardSectionInScopeReturnsWithPoints() {
        SectionEntity section = new SectionEntity();
        section.setId(4L);
        section.setTunnelId(4L);
        when(mapper.selectSections(any(), any())).thenReturn(List.of(section));
        when(mapper.selectLatest(eq(4L), eq(4L), any(), any(), any())).thenReturn(List.of());
        var vo = service.board(4L, DataScope.ofTunnels(Set.of(4L)));
        assertEquals(4L, vo.getSectionId());
        assertEquals(List.of(), vo.getPoints());
    }

    @Test
    void overviewOnlineRateRoundedOneDecimal() {
        OverviewVo vo = new OverviewVo();
        vo.setTotalPoints(3);
        vo.setOnlinePoints(1);
        vo.setTodaySamples(10);
        when(mapper.selectOverview(any(), any())).thenReturn(vo);
        OverviewVo result = service.overview(DataScope.all());
        assertEquals(2, result.getOfflinePoints());
        assertEquals(0, new BigDecimal("33.3").compareTo(result.getOnlineRate()));
        assertNotNull(result);
    }

    @Test
    void pagePointsEmptyTotalSkipsQuery() {
        when(mapper.countPoints(any(), any(), any(), any(), any(), any())).thenReturn(0L);
        var page = service.pagePoints(null, null, null, null, null, 1, 20, DataScope.all());
        assertEquals(0L, page.total());
        verify(mapper, never()).selectPoints(any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void latestListPassesTunnelScope() {
        when(mapper.selectLatest(eq(4L), any(), any(), any(), any())).thenReturn(List.of());
        service.latestList(4L, null, null, null, DataScope.ofTunnels(Set.of(4L)));
        verify(mapper).selectLatest(eq(4L), any(), any(), any(), any());
    }
}
