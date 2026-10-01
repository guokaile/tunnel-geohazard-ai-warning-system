package com.tgaws.web.controller;

import com.tgaws.business.rpt.entity.RptReportEntity;
import com.tgaws.business.rpt.manager.DashboardService;
import com.tgaws.business.rpt.manager.ReportService;
import com.tgaws.common.result.Result;
import com.tgaws.web.security.RequirePermission;
import com.tgaws.web.log.OperLog;
import com.tgaws.web.security.RateLimit;
import com.tgaws.web.security.LoginContext;
import com.tgaws.web.security.ScopeGuard;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 报表与驾驶舱接口（《4》4.5.6 API-F01~F05，T-607）。
 * 注：tunnelId 暂由请求传入，W7 数据权限拦截器接入后改取 DataScope 上下文。
 */
@RestController
@RequestMapping("/api/v1/rpt")
public class RptController {

    private final DashboardService dashboardService;
    private final ReportService reportService;

    public RptController(DashboardService dashboardService, ReportService reportService) {
        this.dashboardService = dashboardService;
        this.reportService = reportService;
    }

    /** API-F01 驾驶舱指标（FR-802，≤1min 刷新：今日实时段小查询+预聚合周趋势） */
    @GetMapping("/dashboard")
    @RequirePermission("rpt:dashboard")
    @RateLimit(RateLimit.Tier.DASHBOARD)
    public Result<DashboardService.DashboardVo> dashboard(@RequestParam long tunnelId) {
        ScopeGuard.requireTunnel(tunnelId);
        return Result.ok(dashboardService.dashboard(tunnelId, LoginContext.getScope()));
    }

    /** API-F02 统计报表（FR-801：type=1日/2周/3月 + period） */
    @GetMapping("/statistics")
    @RequirePermission("rpt:view")
    @RateLimit(RateLimit.Tier.QUERY)
    public Result<ReportService.StatVo> statistics(
            @RequestParam int type,
            @RequestParam String period,
            @RequestParam(required = false) Long tunnelId) {
        if (tunnelId != null) {
            ScopeGuard.requireTunnel(tunnelId);
        }
        return Result.ok(reportService.statistics(type, period, tunnelId, LoginContext.getScope()));
    }

    /** API-F03 分析报告列表（FR-605） */
    @GetMapping("/reports")
    @RequirePermission("rpt:view")
    @RateLimit(RateLimit.Tier.QUERY)
    public Result<List<RptReportEntity>> reports(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) String period) {
        return Result.ok(reportService.listReports(type, period));
    }

    /** API-F04 手动生成报告 */
    @PostMapping("/reports/generate")
    @RequirePermission("rpt:view")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "生成分析报告", module = "报表中心")
    public Result<Map<String, Long>> generate(@RequestParam int type, @RequestParam String period) {
        return Result.ok(Map.of("id", reportService.generateReport(type, period, LoginContext.getScope())));
    }

    /** API-F05 下载报告（附件流） */
    @GetMapping("/reports/{id}/download")
    @RequirePermission("rpt:view")
    @RateLimit(RateLimit.Tier.QUERY)
    public ResponseEntity<Resource> download(@PathVariable long id) {
        String path = reportService.reportPath(id);
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + resource.getFilename() + "\"")
                .contentType(MediaType.TEXT_HTML)
                .body(resource);
    }
}
