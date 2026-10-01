package com.tgaws.business.data.manager;

import com.tgaws.business.data.mapper.PartitionMaintenanceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 分区维护（T-709，NFR-DATA1/《3》3.5.1 落地）：
 *
 * <ul>
 *   <li><b>建分区</b>（每月 1 日 00:10）：REORGANIZE pmax → (未来 3 个月分区, pmax)，
 *       pmax 永远兜底——任务失败数据不丢（D0007 口径），重复执行幂等（先查存在性）；</li>
 *   <li><b>过期删除</b>（NFR-DATA1 原始数据保留 1 年）：逐月 DROP 边界前分区，
 *       删除前行数留痕 data_archive_log（O(1) 无大事务）；</li>
 *   <li><b>每日巡检</b>：pmax 存在 + 最大分区上界 ≥ 当前+90 天 + pmax 有数据告警。</li>
 * </ul>
 */
@Service
public class PartitionMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(PartitionMaintenanceService.class);

    private static final Set<String> TABLES = Set.of("data_sample", "data_sample_minute");
    private static final DateTimeFormatter PARTITION_FMT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter BOUNDARY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final PartitionMaintenanceMapper partitionMapper;

    public PartitionMaintenanceService(PartitionMaintenanceMapper partitionMapper) {
        this.partitionMapper = partitionMapper;
    }

    /** 每月任务：为两表补齐未来 3 个月分区（幂等） */
    @Transactional
    public int ensureFuturePartitions() {
        int created = 0;
        YearMonth cursor = YearMonth.now();
        for (String table : TABLES) {
            Set<String> existing = partitionNames(table);
            for (int i = 1; i <= 3; i++) {
                YearMonth month = cursor.plusMonths(i);
                String partition = "p" + month.format(PARTITION_FMT);
                String boundary = month.plusMonths(1).atDay(1).format(BOUNDARY_FMT);
                if (existing.contains(partition)) {
                    continue;
                }
                partitionMapper.reorganizePmax(table, partition, boundary);
                partitionMapper.insertArchiveLog(table, partition, 1,
                        month.plusMonths(1).atDay(1).atStartOfDay(), null, 1,
                        "每月任务自动创建（REORGANIZE pmax）");
                created++;
                log.info("分区创建：{}.{}（上界 {}）", table, partition, boundary);
            }
        }
        return created;
    }

    /** 过期删除：DROP 边界早于保留线的分区（NFR-DATA1：原始数据 1 年） */
    @Transactional
    public int dropExpiredPartitions() {
        int dropped = 0;
        LocalDate retainBoundary = LocalDate.now().minusYears(1).withDayOfMonth(1);
        for (String table : TABLES) {
            for (Map<String, Object> part : partitionMapper.partitionsOf(table)) {
                String name = String.valueOf(part.get("name"));
                String boundary = String.valueOf(part.get("boundary"));
                if ("pmax".equalsIgnoreCase(name) || "MAXVALUE".equals(boundary)) {
                    continue;
                }
                LocalDate boundaryDate = parseBoundary(boundary);
                if (boundaryDate.isBefore(retainBoundary)) {
                    long rows = partitionMapper.partitionRowCount(table, name);
                    partitionMapper.dropPartition(table, name);
                    partitionMapper.insertArchiveLog(table, name, 2,
                            boundaryDate.atStartOfDay(), rows, 1, "过期归档删除（保留 1 年）");
                    dropped++;
                    log.info("分区删除：{}.{}（{} 行）", table, name, rows);
                }
            }
        }
        return dropped;
    }

    /** 每日巡检：pmax 兜底 + 边界余量 ≥90 天 + pmax 有数据告警 */
    public CheckResult dailyCheck() {
        StringBuilder problems = new StringBuilder();
        LocalDate minBoundary = LocalDate.now().plusDays(90).withDayOfMonth(1);
        for (String table : TABLES) {
            List<Map<String, Object>> parts = partitionMapper.partitionsOf(table);
            boolean pmax = parts.stream().anyMatch(p -> "pmax".equalsIgnoreCase(String.valueOf(p.get("name"))));
            if (!pmax) {
                problems.append(table).append(" 缺 pmax 兜底分区;");
            }
            LocalDate maxBoundary = parts.stream()
                    .map(p -> String.valueOf(p.get("boundary")))
                    .filter(b -> !"MAXVALUE".equals(b) && !"pmax".equals(b))
                    .map(this::parseBoundary)
                    .filter(java.util.Objects::nonNull)
                    .max(LocalDate::compareTo).orElse(null);
            if (maxBoundary == null || maxBoundary.isBefore(minBoundary)) {
                problems.append(table).append(" 最大分区上界不足 +90 天;");
            }
            long pmaxRows = partitionMapper.partitionRowCount(table, "pmax");
            if (pmaxRows > 0) {
                problems.append(table).append(" pmax 有数据 ").append(pmaxRows).append(" 行（建分区失败遗留，需人工介入）;");
            }
        }
        boolean healthy = problems.length() == 0;
        log.info("分区每日巡检：{}（{}）", healthy ? "正常" : "异常", problems);
        return new CheckResult(healthy, problems.toString());
    }

    /** information_schema 分区描述带单引号（'2027-07-01'），解析前剥离 */
    private LocalDate parseBoundary(String raw) {
        try {
            return LocalDate.parse(raw.replace("'", "").trim(), BOUNDARY_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    private Set<String> partitionNames(String table) {
        return partitionMapper.partitionsOf(table).stream()
                .map(p -> String.valueOf(p.get("name")))
                .collect(java.util.stream.Collectors.toSet());
    }

    /** 巡检结果 */
    public record CheckResult(boolean healthy, String problems) {
    }
}
