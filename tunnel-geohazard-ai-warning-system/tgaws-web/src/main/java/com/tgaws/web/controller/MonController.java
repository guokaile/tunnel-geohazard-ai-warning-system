package com.tgaws.web.controller;

import com.tgaws.business.mon.entity.PointEntity;
import com.tgaws.business.mon.entity.SectionEntity;
import com.tgaws.business.mon.entity.TunnelEntity;
import com.tgaws.business.mon.manager.MonQueryService;
import com.tgaws.business.mon.vo.MonQueryVos.OverviewVo;
import com.tgaws.business.mon.vo.MonQueryVos.PointLatestVo;
import com.tgaws.business.mon.vo.MonQueryVos.PointStatsVo;
import com.tgaws.business.mon.vo.MonQueryVos.SectionBoardVo;
import com.tgaws.business.mon.vo.MonQueryVos.SeriesPointVo;
import com.tgaws.common.result.PageResult;
import com.tgaws.common.result.Result;
import com.tgaws.web.security.LoginContext;
import com.tgaws.web.security.RateLimit;
import com.tgaws.web.security.RequirePermission;
import com.tgaws.web.security.ScopeGuard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 监控域查询接口（《4》API-B01/B03/B05/B06/B16~B20，T-811——实时监控中心 F2 数据源）。
 * 台账写路径（B02/B04/B07~B10）与导入（B11~B15）不在本控制器范围。
 */
@RestController
@RequestMapping("/api/v1/mon")
public class MonController {

    private static final int MAX_PAGE_SIZE = 200;

    private final MonQueryService monQueryService;

    public MonController(MonQueryService monQueryService) {
        this.monQueryService = monQueryService;
    }

    /** API-B01 隧道列表（数据范围过滤） */
    @GetMapping("/tunnels")
    @RequirePermission("mon:point:view")
    @RateLimit(RateLimit.Tier.DASHBOARD)
    public Result<List<TunnelEntity>> tunnels() {
        return Result.ok(monQueryService.listTunnels(LoginContext.getScope()));
    }

    /** API-B03 断面列表（数据范围过滤） */
    @GetMapping("/sections")
    @RequirePermission("mon:point:view")
    @RateLimit(RateLimit.Tier.DASHBOARD)
    public Result<List<SectionEntity>> sections(@RequestParam(required = false) Long tunnelId) {
        if (tunnelId != null) {
            ScopeGuard.requireTunnel(tunnelId);
        }
        return Result.ok(monQueryService.listSections(tunnelId, LoginContext.getScope()));
    }

    /** API-B05 点位分页（数据范围过滤） */
    @GetMapping("/points")
    @RequirePermission("mon:point:view")
    @RateLimit(RateLimit.Tier.QUERY)
    public Result<PageResult<PointEntity>> points(
            @RequestParam(required = false) Long tunnelId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) Integer hazardType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        if (tunnelId != null) {
            ScopeGuard.requireTunnel(tunnelId);
        }
        pageNum = Math.max(pageNum, 1);
        pageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        return Result.ok(monQueryService.pagePoints(tunnelId, sectionId, hazardType, keyword,
                status, pageNum, pageSize, LoginContext.getScope()));
    }

    /** API-B06 点位详情（数据出口：越权 C0008） */
    @GetMapping("/points/{id}")
    @RequirePermission("mon:point:view")
    @RateLimit(RateLimit.Tier.QUERY)
    public Result<PointEntity> pointDetail(@PathVariable long id) {
        PointEntity point = monQueryService.getPoint(id);
        ScopeGuard.requireTunnel(point.getTunnelId());
        return Result.ok(point);
    }

    /** API-B16 实时最新值列表（5s 轮询数据源，DASHBOARD 限流档） */
    @GetMapping("/latest")
    @RequirePermission("mon:data:view")
    @RateLimit(RateLimit.Tier.DASHBOARD)
    public Result<List<PointLatestVo>> latest(
            @RequestParam(required = false) Long tunnelId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) Integer hazardType,
            @RequestParam(required = false) String keyword) {
        if (tunnelId != null) {
            ScopeGuard.requireTunnel(tunnelId);
        }
        return Result.ok(monQueryService.latestList(tunnelId, sectionId, hazardType, keyword,
                LoginContext.getScope()));
    }

    /** API-B17 时序数据（granularity=raw|minute；raw 最长 7 天） */
    @GetMapping("/points/{id}/series")
    @RequirePermission("mon:data:view")
    @RateLimit(RateLimit.Tier.QUERY)
    public Result<List<SeriesPointVo>> series(@PathVariable long id,
                                              @RequestParam String from,
                                              @RequestParam String to,
                                              @RequestParam(defaultValue = "raw") String granularity) {
        PointEntity point = monQueryService.getPoint(id);
        ScopeGuard.requireTunnel(point.getTunnelId());
        return Result.ok(monQueryService.series(id, LocalDateTime.parse(from),
                LocalDateTime.parse(to), granularity));
    }

    /** API-B18 统计值（max/min/avg/rate） */
    @GetMapping("/points/{id}/stats")
    @RequirePermission("mon:data:view")
    @RateLimit(RateLimit.Tier.QUERY)
    public Result<PointStatsVo> stats(@PathVariable long id,
                                      @RequestParam String from,
                                      @RequestParam String to) {
        PointEntity point = monQueryService.getPoint(id);
        ScopeGuard.requireTunnel(point.getTunnelId());
        return Result.ok(monQueryService.stats(id, LocalDateTime.parse(from),
                LocalDateTime.parse(to)));
    }

    /** API-B19 断面图数据（断面元信息+点位分布实时值） */
    @GetMapping("/sections/{id}/board")
    @RequirePermission("mon:data:view")
    @RateLimit(RateLimit.Tier.DASHBOARD)
    public Result<SectionBoardVo> board(@PathVariable long id) {
        return Result.ok(monQueryService.board(id, LoginContext.getScope()));
    }

    /** API-B20 概览统计（点位总数/在线率/今日数据量） */
    @GetMapping("/overview")
    @RequirePermission("mon:data:view")
    @RateLimit(RateLimit.Tier.DASHBOARD)
    public Result<OverviewVo> overview() {
        return Result.ok(monQueryService.overview(LoginContext.getScope()));
    }
}
