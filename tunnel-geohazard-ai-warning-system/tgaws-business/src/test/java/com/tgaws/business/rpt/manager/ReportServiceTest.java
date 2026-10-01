package com.tgaws.business.rpt.manager;

import com.tgaws.business.rpt.entity.RptReportEntity;
import com.tgaws.business.rpt.entity.RptStatDailyEntity;
import com.tgaws.business.rpt.mapper.RptReportMapper;
import com.tgaws.common.exception.BizException;
import com.tgaws.common.result.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 统计报表与分析报告单测（FR-801/605：周期解析/比率口径/报告落盘 D 盘/下载守卫）。
 */
class ReportServiceTest {

    /** 测试报告目录（C 盘纪律：测试落盘也走 D 盘） */
    private static final Path TEST_DIR = Path.of("D:/AI监测预警系统/.tgaws-run/test-rpt");

    private RptStatDailyService statDailyService;
    private RptReportMapper reportMapper;
    private ReportService service;

    @BeforeEach
    void setUp() throws IOException {
        statDailyService = mock(RptStatDailyService.class);
        reportMapper = mock(RptReportMapper.class);
        org.mockito.Mockito.doAnswer(inv -> {
            RptReportEntity e = inv.getArgument(0);
            e.setId(900L);
            return 1;
        }).when(reportMapper).insert(any(RptReportEntity.class));
        service = new ReportService(statDailyService, reportMapper, TEST_DIR.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        if (Files.exists(TEST_DIR)) {
            try (var walk = Files.walk(TEST_DIR)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // 清理失败不影响断言
                    }
                });
            }
        }
    }

    private RptStatDailyEntity row(LocalDate date, int warnTotal, int disposeTotal, int disposeClosed) {
        RptStatDailyEntity e = new RptStatDailyEntity();
        e.setStatDate(date);
        e.setWarnTotal(warnTotal);
        e.setWarnRed(1);
        e.setWarnConfirmed(2);
        e.setWarnClosed(1);
        e.setDisposeTotal(disposeTotal);
        e.setDisposeClosed(disposeClosed);
        e.setPatrolTotal(10);
        e.setPatrolDone(9);
        e.setHazardNew(3);
        e.setHazardClosed(2);
        e.setSampleCount(1000L);
        e.setSampleExpect(1000L);
        return e;
    }

    @Test
    void parsePeriodDailyWeeklyMonthly() {
        assertEquals(LocalDate.of(2026, 9, 28), ReportService.parsePeriod(1, "20260928").from());
        // 2026 ISO 第 39 周：周一 2026-09-21 ~ 周日 2026-09-27
        ReportService.PeriodRange week = ReportService.parsePeriod(2, "202639");
        assertEquals(LocalDate.of(2026, 9, 21), week.from());
        assertEquals(LocalDate.of(2026, 9, 27), week.to());
        ReportService.PeriodRange month = ReportService.parsePeriod(3, "202609");
        assertEquals(LocalDate.of(2026, 9, 1), month.from());
        assertEquals(LocalDate.of(2026, 9, 30), month.to());
    }

    @Test
    void parsePeriodRejectsBadInput() {
        assertEquals(ErrorCode.A0002, assertThrows(BizException.class, () ->
                ReportService.parsePeriod(9, "20260928")).getErrorCode(), "报表类型必须 1/2/3");
        assertEquals(ErrorCode.A0002, assertThrows(BizException.class, () ->
                ReportService.parsePeriod(1, "bad")).getErrorCode(), "period 格式错误");
        assertEquals(ErrorCode.A0002, assertThrows(BizException.class, () ->
                ReportService.parsePeriod(1, null)).getErrorCode(), "period 必填");
    }

    @Test
    void statisticsSumsWithRates() {
        LocalDate d = LocalDate.of(2026, 9, 28);
        when(statDailyService.rangeRows(isNull(), eq(d), eq(d), any())).thenReturn(List.of(
                row(d, 10, 10, 8), row(d, 5, 0, 0)));
        ReportService.StatVo vo = service.statistics(1, "20260928", null, com.tgaws.common.datascope.DataScope.all());
        verify(statDailyService).ensureAggregated(eq(d), eq(d));
        assertEquals(15L, vo.warnTotal());
        assertEquals(2L, vo.warnRed());
        assertEquals(10L, vo.disposeTotal());
        assertEquals(8L, vo.disposeClosed());
        assertEquals(0.8, vo.closeRate());
        assertEquals(0.9, vo.patrolRate(), "9/10 巡检完成率");
        assertEquals(1.0, vo.sampleRate(), "实收=应收 → 100%");
    }

    @Test
    void statisticsZeroDenominatorRatesFull() {
        LocalDate d = LocalDate.of(2026, 9, 28);
        RptStatDailyEntity empty = row(d, 0, 0, 0);
        empty.setPatrolTotal(0);
        empty.setPatrolDone(0);
        when(statDailyService.rangeRows(isNull(), eq(d), eq(d), any())).thenReturn(List.of(empty));
        ReportService.StatVo vo = service.statistics(1, "20260928", null, com.tgaws.common.datascope.DataScope.all());
        assertEquals(1.0, vo.closeRate(), "分母 0 → 无欠账计 100%");
        assertEquals(1.0, vo.patrolRate());
    }

    @Test
    void generateReportWritesHtmlFileOnD() throws IOException {
        LocalDate d = LocalDate.of(2026, 9, 28);
        when(statDailyService.rangeRows(isNull(), eq(d), eq(d), any())).thenReturn(List.of(
                row(d, 10, 10, 7)));
        long id = service.generateReport(1, "20260928", com.tgaws.common.datascope.DataScope.all());
        assertEquals(900L, id);
        Path file = TEST_DIR.resolve("r1/rpt_1_20260928.html");
        assertTrue(Files.exists(file), "报告文件落盘 D 盘测试目录");
        String html = Files.readString(file);
        assertTrue(html.contains("监测分析日报"), "含报告标题");
        assertTrue(html.contains("近 7 日预警趋势"), "FR-605 含趋势图");
        assertTrue(html.contains("结论建议"), "FR-605 含结论建议");
        assertTrue(html.contains("低于 80%"), "闭环率 7/10=70% 触发督办建议");
        verify(reportMapper).insert(any(RptReportEntity.class));
    }

    @Test
    void generateReportSuggestsNormalWhenAllGood() throws IOException {
        LocalDate d = LocalDate.of(2026, 9, 28);
        RptStatDailyEntity good = row(d, 0, 10, 10);
        good.setWarnRed(0); // 无红级预警：全达标 → 正常结论
        when(statDailyService.rangeRows(isNull(), eq(d), eq(d), any())).thenReturn(List.of(good));
        service.generateReport(1, "20260928", com.tgaws.common.datascope.DataScope.all());
        String html = Files.readString(TEST_DIR.resolve("r1/rpt_1_20260928.html"));
        assertTrue(html.contains("各项指标正常"), "全达标时输出正常结论");
    }

    @Test
    void reportPathGuardsMissingOrFailed() {
        when(reportMapper.selectById(404L)).thenReturn(null);
        assertEquals(ErrorCode.B0802, assertThrows(BizException.class, () ->
                service.reportPath(404L)).getErrorCode(), "报告不存在 → B0802");
        RptReportEntity failed = new RptReportEntity();
        failed.setId(405L);
        failed.setStatus(0);
        when(reportMapper.selectById(405L)).thenReturn(failed);
        assertEquals(ErrorCode.B0802, assertThrows(BizException.class, () ->
                service.reportPath(405L)).getErrorCode(), "生成失败的报告不可下载");
        RptReportEntity ok = new RptReportEntity();
        ok.setId(406L);
        ok.setStatus(1);
        ok.setFilePath("D:/x.html");
        when(reportMapper.selectById(406L)).thenReturn(ok);
        assertEquals("D:/x.html", service.reportPath(406L));
    }

    @Test
    void listReportsDelegates() {
        service.listReports(1, "20260928");
        verify(reportMapper).selectList(eq(1), eq("20260928"));
    }
}
