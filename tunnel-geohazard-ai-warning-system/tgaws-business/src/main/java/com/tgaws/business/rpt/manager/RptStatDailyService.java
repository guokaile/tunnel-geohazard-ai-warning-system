package com.tgaws.business.rpt.manager;

import com.tgaws.business.rpt.entity.RptStatDailyEntity;
import com.tgaws.business.rpt.mapper.RptStatDailyMapper;
import com.tgaws.common.datascope.DataScope;
import com.tgaws.common.datascope.DataScopeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 报表日聚合（FR-801/802 预聚合口径，审计 P0/P2 结论落地）：
 *
 * <ul>
 *   <li>完整日全量聚合落 rpt_stat_daily；驾驶舱"今日"走实时段小查询——
 *       实时 GROUP BY 扫千万行（30~180s）不可能满足 FR-802 ≤1min 刷新；</li>
 *   <li>upsert 重跑安全：同 (tunnel_id, stat_date) 覆盖全部指标列，
 *       任务重跑/补数零副作用（uk_tunnel_date）；</li>
 *   <li>数据完整率：sample_expect 按点位采集频率在聚合时固化（审计 P2-14）。</li>
 * </ul>
 */
@Service
public class RptStatDailyService {

    private static final Logger log = LoggerFactory.getLogger(RptStatDailyService.class);

    private static final ZoneId DB_ZONE = ZoneId.of("Asia/Shanghai");
    private static final long SECONDS_PER_DAY = 86_400L;

    private final RptStatDailyMapper statMapper;

    public RptStatDailyService(RptStatDailyMapper statMapper) {
        this.statMapper = statMapper;
    }

    /** 聚合指定日期（可含今天：报表查询时取快照口径） */
    @Transactional
    public int aggregateForDate(LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();

        Map<Long, long[]> warn = toTunnelMetric(statMapper.warnStatByTunnel(from, to),
                "total", "red", "confirmed", "closed");
        Map<Long, long[]> dispose = toTunnelMetric(statMapper.disposeStatByTunnel(from, to),
                "total", "closed");
        Map<Long, long[]> patrol = toTunnelMetric(statMapper.patrolStatByTunnel(from, to),
                "total", "done");
        Map<Long, long[]> hazard = toTunnelMetric(statMapper.hazardStatByTunnel(from, to),
                "newCnt", "closed");

        // 数据完整率：逐点位实收 + 按频率固化的应收
        Map<Long, Long> sampleByPoint = new HashMap<>();
        for (Map<String, Object> row : statMapper.sampleCountByPoint(from, to)) {
            sampleByPoint.put(asLong(row, "pointId"), asLong(row, "cnt"));
        }
        Map<Long, long[]> sample = new HashMap<>(); // tunnelId -> [count, expect]
        for (Map<String, Object> point : statMapper.pointFreqByTunnel()) {
            long tunnelId = asLong(point, "tunnelId");
            long pointId = asLong(point, "pointId");
            long freq = asLong(point, "freq");
            long count = sampleByPoint.getOrDefault(pointId, 0L);
            long expect = freq > 0 ? SECONDS_PER_DAY / freq : 0L;
            long[] acc = sample.computeIfAbsent(tunnelId, k -> new long[2]);
            acc[0] += count;
            acc[1] += expect;
        }

        Set<Long> tunnels = new HashSet<>();
        tunnels.addAll(warn.keySet());
        tunnels.addAll(dispose.keySet());
        tunnels.addAll(patrol.keySet());
        tunnels.addAll(hazard.keySet());
        tunnels.addAll(sample.keySet());

        int rows = 0;
        for (Long tunnelId : tunnels) {
            RptStatDailyEntity e = new RptStatDailyEntity();
            e.setTunnelId(tunnelId);
            e.setStatDate(date);
            e.setWarnTotal((int) metric(warn, tunnelId, 0));
            e.setWarnRed((int) metric(warn, tunnelId, 1));
            e.setWarnConfirmed((int) metric(warn, tunnelId, 2));
            e.setWarnClosed((int) metric(warn, tunnelId, 3));
            e.setDisposeTotal((int) metric(dispose, tunnelId, 0));
            e.setDisposeClosed((int) metric(dispose, tunnelId, 1));
            e.setPatrolTotal((int) metric(patrol, tunnelId, 0));
            e.setPatrolDone((int) metric(patrol, tunnelId, 1));
            e.setHazardNew((int) metric(hazard, tunnelId, 0));
            e.setHazardClosed((int) metric(hazard, tunnelId, 1));
            e.setSampleCount(metric(sample, tunnelId, 0));
            e.setSampleExpect(metric(sample, tunnelId, 1));
            statMapper.upsert(e);
            rows++;
        }
        if (rows > 0) {
            log.info("报表日聚合完成：{}（{} 隧道）", date, rows);
        }
        return rows;
    }

    /** 补齐缺失日期的聚合（幂等：已有日期跳过） */
    @Transactional
    public int ensureAggregated(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            return 0;
        }
        Set<LocalDate> existing = new HashSet<>(statMapper.selectExistingDates(from, to));
        int rows = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (!existing.contains(date)) {
                rows += aggregateForDate(date);
            }
        }
        return rows;
    }

    /** 区间日聚合行（驾驶舱周趋势/统计报表；scope 经 DataScopeHelper 构建） */
    public List<RptStatDailyEntity> rangeRows(Long tunnelId, LocalDate from, LocalDate to,
                                              DataScope scope) {
        return statMapper.selectRange(tunnelId, from, to, DataScopeHelper.forTunnel(scope));
    }

    /** 今天（DB 时区） */
    public static LocalDate today() {
        return LocalDate.now(DB_ZONE);
    }

    /**
     * 今日数据完整率分子分母（实时段）：实收样本 vs 期望=按频率×自零点已过秒数。
     * 仅扫当日分区（主键范围扫描），驾驶舱 ≤1min 刷新有界。
     *
     * @return [sampleCount, sampleExpect]
     */
    public long[] todaySamples(long tunnelId) {
        LocalDateTime dayStart = LocalDate.now(DB_ZONE).atStartOfDay();
        LocalDateTime now = LocalDateTime.now(DB_ZONE);
        long elapsed = Math.min(SECONDS_PER_DAY,
                java.time.Duration.between(dayStart, now).getSeconds());

        Map<Long, Long> sampleByPoint = new HashMap<>();
        for (Map<String, Object> row : statMapper.sampleCountByPoint(dayStart, now)) {
            sampleByPoint.put(asLong(row, "pointId"), asLong(row, "cnt"));
        }
        long count = 0L;
        long expect = 0L;
        for (Map<String, Object> point : statMapper.pointFreqByTunnel()) {
            if (asLong(point, "tunnelId") != tunnelId) {
                continue;
            }
            long freq = asLong(point, "freq");
            count += sampleByPoint.getOrDefault(asLong(point, "pointId"), 0L);
            expect += freq > 0 ? elapsed / freq : 0L;
        }
        return new long[]{count, expect};
    }

    // ==================== 内部工具 ====================

    private Map<Long, long[]> toTunnelMetric(List<Map<String, Object>> rows, String... cols) {
        Map<Long, long[]> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            long tunnelId = asLong(row, "tunnelId");
            long[] v = new long[cols.length];
            for (int i = 0; i < cols.length; i++) {
                v[i] = asLong(row, cols[i]);
            }
            result.put(tunnelId, v);
        }
        return result;
    }

    private long metric(Map<Long, long[]> map, long tunnelId, int idx) {
        long[] v = map.get(tunnelId);
        return v == null ? 0L : v[idx];
    }

    static long asLong(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v == null) {
            return 0L;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(v));
    }
}
