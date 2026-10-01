package com.tgaws.business.mon.mapper;

import com.tgaws.business.mon.entity.PointEntity;
import com.tgaws.business.mon.entity.SectionEntity;
import com.tgaws.business.mon.entity.TunnelEntity;
import com.tgaws.business.mon.vo.MonQueryVos.OverviewVo;
import com.tgaws.business.mon.vo.MonQueryVos.PointLatestVo;
import com.tgaws.business.mon.vo.MonQueryVos.PointStatsVo;
import com.tgaws.business.mon.vo.MonQueryVos.SeriesPointVo;
import com.tgaws.common.datascope.DataScopeCondition;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 监控域查询 Mapper（T-811：B01/B03/B05/B06/B16~B20 实时监控中心读路径）。
 *
 * <p>数据权限口径与《6》6.3.6 一致：列表类查询方法签名强制携带
 * {@link DataScopeCondition}（tunnel_id 或 prj_tunnel.id 列，见 DataScopeHelper），
 * 点级详情（B06/B17/B18）由 Controller 先 ScopeGuard.requireTunnel 出口校验。</p>
 */
public interface MonQueryMapper {

    /** B01 隧道列表（enabled，数据范围过滤） */
    List<TunnelEntity> selectTunnels(@Param("scope") DataScopeCondition scope);

    /** B03 断面列表（按隧道+数据范围） */
    List<SectionEntity> selectSections(@Param("tunnelId") Long tunnelId,
                                       @Param("scope") DataScopeCondition scope);

    /** B05 点位分页（台账字段，数据范围过滤） */
    List<PointEntity> selectPoints(@Param("tunnelId") Long tunnelId,
                                   @Param("sectionId") Long sectionId,
                                   @Param("hazardType") Integer hazardType,
                                   @Param("keyword") String keyword,
                                   @Param("status") Integer status,
                                   @Param("scope") DataScopeCondition scope,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    /** B05 点位分页计数 */
    long countPoints(@Param("tunnelId") Long tunnelId,
                     @Param("sectionId") Long sectionId,
                     @Param("hazardType") Integer hazardType,
                     @Param("keyword") String keyword,
                     @Param("status") Integer status,
                     @Param("scope") DataScopeCondition scope);

    /** B06 点位详情（台账全字段） */
    PointEntity selectPointById(@Param("id") long id);

    /**
     * B16 实时最新值列表（mon_point 联查 data_point_latest、prj_tunnel；
     * warnLevel 取该点位未消警事件最高级别，LEFT JOIN 不丢无事件点位）。
     */
    List<PointLatestVo> selectLatest(@Param("tunnelId") Long tunnelId,
                                     @Param("sectionId") Long sectionId,
                                     @Param("hazardType") Integer hazardType,
                                     @Param("keyword") String keyword,
                                     @Param("scope") DataScopeCondition scope);

    /** B17 原始粒度时序（raw，≤7 天窗口由服务层校验，上限防深分页） */
    List<SeriesPointVo> selectSeriesRaw(@Param("pointId") long pointId,
                                        @Param("fromTs") LocalDateTime fromTs,
                                        @Param("toTs") LocalDateTime toTs,
                                        @Param("limit") int limit);

    /** B17 分钟粒度时序（data_sample_minute 窗口均值） */
    List<SeriesPointVo> selectSeriesMinute(@Param("pointId") long pointId,
                                           @Param("fromTs") LocalDateTime fromTs,
                                           @Param("toTs") LocalDateTime toTs);

    /** B18 统计聚合（max/min/avg/首末值；rate 由服务层按时间跨度计算） */
    List<SeriesPointVo> selectSeriesFirstLast(@Param("pointId") long pointId,
                                              @Param("fromTs") LocalDateTime fromTs,
                                              @Param("toTs") LocalDateTime toTs);

    /** B18 统计聚合（max/min/avg；无样本返回 null） */
    PointStatsVo selectSeriesAgg(@Param("pointId") long pointId,
                                 @Param("fromTs") LocalDateTime fromTs,
                                 @Param("toTs") LocalDateTime toTs);

    /** B20 概览：点位总数/在线/离线 + 今日入库样本数（数据范围过滤） */
    OverviewVo selectOverview(@Param("scope") DataScopeCondition scope,
                              @Param("todayStart") LocalDateTime todayStart);
}
