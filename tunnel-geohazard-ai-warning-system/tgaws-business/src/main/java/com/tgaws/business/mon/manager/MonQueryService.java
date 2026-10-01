package com.tgaws.business.mon.manager;

import com.tgaws.business.mon.entity.PointEntity;
import com.tgaws.business.mon.entity.SectionEntity;
import com.tgaws.business.mon.entity.TunnelEntity;
import com.tgaws.business.mon.mapper.MonQueryMapper;
import com.tgaws.business.mon.vo.MonQueryVos.OverviewVo;
import com.tgaws.business.mon.vo.MonQueryVos.PointLatestVo;
import com.tgaws.business.mon.vo.MonQueryVos.PointStatsVo;
import com.tgaws.business.mon.vo.MonQueryVos.SectionBoardVo;
import com.tgaws.business.mon.vo.MonQueryVos.SeriesPointVo;
import com.tgaws.common.datascope.DataScope;
import com.tgaws.common.datascope.DataScopeCondition;
import com.tgaws.common.datascope.DataScopeHelper;
import com.tgaws.common.exception.BizException;
import com.tgaws.common.result.ErrorCode;
import com.tgaws.common.result.PageResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 监控域查询服务（T-811：B01/B03/B05/B06/B16~B20 实时监控中心读路径）。
 *
 * <p>数据权限口径（《6》6.3.6）：列表查询由 DataScopeHelper 构建条件过滤；
 * 点级详情/时序/统计由 Controller 先 ScopeGuard.requireTunnel（C0008 出口）再进入本服务。</p>
 */
@Service
public class MonQueryService {

    /** raw 粒度查询窗口上限（天，契约 B17：raw≤7 天，超出走 minute） */
    private static final int RAW_MAX_DAYS = 7;
    /** raw 粒度单次返回上限（防深分页，超出由前端缩小窗口） */
    private static final int RAW_MAX_POINTS = 50000;
    /** minute 粒度单次返回上限 */
    private static final int MINUTE_MAX_POINTS = 100000;

    private final MonQueryMapper monQueryMapper;

    public MonQueryService(MonQueryMapper monQueryMapper) {
        this.monQueryMapper = monQueryMapper;
    }

    /** B01 隧道列表（启用，数据范围过滤） */
    public List<TunnelEntity> listTunnels(DataScope scope) {
        return monQueryMapper.selectTunnels(DataScopeHelper.forTunnelList(scope));
    }

    /** B03 断面列表（tunnelId 可空=范围内全部；数据范围过滤） */
    public List<SectionEntity> listSections(Long tunnelId, DataScope scope) {
        return monQueryMapper.selectSections(tunnelId, DataScopeHelper.forTunnel(scope));
    }

    /** B05 点位分页（台账字段，数据范围过滤） */
    public PageResult<PointEntity> pagePoints(Long tunnelId, Long sectionId, Integer hazardType,
                                              String keyword, Integer status,
                                              int pageNum, int pageSize, DataScope scope) {
        DataScopeCondition condition = DataScopeHelper.forTunnel(scope);
        long total = monQueryMapper.countPoints(tunnelId, sectionId, hazardType, keyword, status, condition);
        if (total == 0) {
            return new PageResult<>(0L, List.of());
        }
        int offset = (pageNum - 1) * pageSize;
        List<PointEntity> list = monQueryMapper.selectPoints(tunnelId, sectionId, hazardType,
                keyword, status, condition, offset, pageSize);
        return new PageResult<>(total, list);
    }

    /** B06 点位详情（出口校验在 Controller） */
    public PointEntity getPoint(long id) {
        PointEntity point = monQueryMapper.selectPointById(id);
        if (point == null) {
            throw new BizException(ErrorCode.B0101);
        }
        return point;
    }

    /** B16 实时最新值列表（数据范围过滤） */
    public List<PointLatestVo> latestList(Long tunnelId, Long sectionId, Integer hazardType,
                                          String keyword, DataScope scope) {
        return monQueryMapper.selectLatest(tunnelId, sectionId, hazardType, keyword,
                DataScopeHelper.forTunnel(scope));
    }

    /**
     * B17 时序数据。
     *
     * @param granularity raw（原始，≤7 天）| minute（分钟聚合）
     */
    public List<SeriesPointVo> series(long pointId, LocalDateTime from, LocalDateTime to,
                                      String granularity) {
        if ("minute".equalsIgnoreCase(granularity)) {
            return monQueryMapper.selectSeriesMinute(pointId, from, to);
        }
        if (Duration.between(from, to).toDays() > RAW_MAX_DAYS) {
            throw new BizException(ErrorCode.B0116);
        }
        return monQueryMapper.selectSeriesRaw(pointId, from, to, RAW_MAX_POINTS);
    }

    /** B18 统计值（max/min/avg + 变化速率=值差/小时） */
    public PointStatsVo stats(long pointId, LocalDateTime from, LocalDateTime to) {
        PointStatsVo vo = monQueryMapper.selectSeriesAgg(pointId, from, to);
        if (vo == null || vo.getMax() == null) {
            // 无样本：全零而非 null，前端图表零值友好
            return zeroStats();
        }
        List<SeriesPointVo> firstLast = monQueryMapper.selectSeriesFirstLast(pointId, from, to);
        BigDecimal rate = BigDecimal.ZERO;
        if (firstLast.size() == 2) {
            SeriesPointVo first = firstLast.get(0);
            SeriesPointVo last = firstLast.get(1);
            long hours = Duration.between(first.getTs(), last.getTs()).toMillis();
            if (hours > 0) {
                BigDecimal delta = last.getValue().subtract(first.getValue());
                rate = delta.divide(BigDecimal.valueOf(hours / 3600000.0), 4, RoundingMode.HALF_UP);
            }
        }
        vo.setRate(rate);
        return vo;
    }

    /** B19 断面图：断面元信息 + 断面点位实时值分布 */
    public SectionBoardVo board(long sectionId, DataScope scope) {
        List<SectionEntity> sections = monQueryMapper.selectSections(null,
                DataScopeHelper.forTunnel(scope));
        SectionEntity section = sections.stream()
                .filter(s -> s.getId() == sectionId)
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.B0115));
        SectionBoardVo vo = new SectionBoardVo();
        vo.setSectionId(section.getId());
        vo.setSectionCode(section.getSectionCode());
        vo.setSectionName(section.getSectionName());
        vo.setMileageFrom(section.getMileageFrom());
        vo.setMileageTo(section.getMileageTo());
        vo.setGeoZone(section.getGeoZone());
        vo.setPoints(monQueryMapper.selectLatest(section.getTunnelId(), sectionId, null, null,
                DataScopeHelper.forTunnel(scope)));
        return vo;
    }

    /** B20 概览统计（在线率一位小数） */
    public OverviewVo overview(DataScope scope) {
        OverviewVo vo = monQueryMapper.selectOverview(DataScopeHelper.forTunnel(scope),
                LocalDate.now().atStartOfDay());
        long total = vo.getTotalPoints();
        long online = vo.getOnlinePoints();
        vo.setOfflinePoints(total - online);
        vo.setOnlineRate(total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(online * 100.0 / total)
                        .setScale(1, RoundingMode.HALF_UP));
        return vo;
    }

    private PointStatsVo zeroStats() {
        PointStatsVo vo = new PointStatsVo();
        vo.setMax(BigDecimal.ZERO);
        vo.setMin(BigDecimal.ZERO);
        vo.setAvg(BigDecimal.ZERO);
        vo.setRate(BigDecimal.ZERO);
        return vo;
    }
}
