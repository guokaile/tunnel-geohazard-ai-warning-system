package com.tgaws.business.rpt.manager;

import com.tgaws.business.rpt.entity.RptReportEntity;
import com.tgaws.business.rpt.mapper.RptReportMapper;
import com.tgaws.common.datascope.DataScope;
import com.tgaws.common.exception.BizException;
import com.tgaws.common.result.ErrorCode;
import com.tgaws.common.util.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 统计报表与分析报告（FR-801/605）：
 *
 * <ul>
 *   <li>F02 统计报表：rpt_stat_daily 区间 SUM（缺日自动补齐，幂等 upsert）；</li>
 *   <li>F03~F05 报告：HTML 报告（统计表 + 7 日趋势 SVG + 结论建议）落
 *       tgaws.rpt.dir（D 盘，C 盘纪律），rpt_report 记录 + 下载。</li>
 * </ul>
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    /** 结论建议阈值（报告自动生成，FR-605） */
    private static final double CLOSE_RATE_LINE = 0.80;
    private static final double PATROL_RATE_LINE = 0.90;
    private static final double SAMPLE_RATE_LINE = 0.95;

    private final RptStatDailyService statDailyService;
    private final RptReportMapper reportMapper;
    private final String rptDir;
    private final SnowflakeIdGenerator idGenerator = SnowflakeIdGenerator.defaultInstance();

    public ReportService(RptStatDailyService statDailyService, RptReportMapper reportMapper,
                         @Value("${tgaws.rpt.dir:D:/AI监测预警系统/reports}") String rptDir) {
        this.statDailyService = statDailyService;
        this.reportMapper = reportMapper;
        this.rptDir = rptDir;
    }

    // ==================== F02 统计报表 ====================

    /** 统计报表数据（日/周/月；type=1日 2周 3月；period=YYYYMMDD/YYYYWW/YYYYMM） */
    public StatVo statistics(int type, String period, Long tunnelId, DataScope scope) {
        PeriodRange range = parsePeriod(type, period);
        statDailyService.ensureAggregated(range.from(), range.to());
        return sum(statDailyService.rangeRows(tunnelId, range.from(), range.to(), scope));
    }

    private StatVo sum(List<com.tgaws.business.rpt.entity.RptStatDailyEntity> rows) {
        long warnTotal = 0, warnRed = 0, warnConfirmed = 0, warnClosed = 0;
        long disposeTotal = 0, disposeClosed = 0, patrolTotal = 0, patrolDone = 0;
        long hazardNew = 0, hazardClosed = 0, sampleCount = 0, sampleExpect = 0;
        for (com.tgaws.business.rpt.entity.RptStatDailyEntity r : rows) {
            warnTotal += r.getWarnTotal();
            warnRed += r.getWarnRed();
            warnConfirmed += r.getWarnConfirmed();
            warnClosed += r.getWarnClosed();
            disposeTotal += r.getDisposeTotal();
            disposeClosed += r.getDisposeClosed();
            patrolTotal += r.getPatrolTotal();
            patrolDone += r.getPatrolDone();
            hazardNew += r.getHazardNew();
            hazardClosed += r.getHazardClosed();
            sampleCount += r.getSampleCount();
            sampleExpect += r.getSampleExpect();
        }
        return new StatVo(warnTotal, warnRed, warnConfirmed, warnClosed,
                disposeTotal, disposeClosed, rate(disposeClosed, disposeTotal),
                patrolTotal, patrolDone, rate(patrolDone, patrolTotal),
                hazardNew, hazardClosed,
                sampleCount, sampleExpect, rate(sampleCount, sampleExpect));
    }

    /** 比率口径：分母为 0 计 100%（无欠账）；否则四舍五入到 0.0001 */
    private static double rate(long numerator, long denominator) {
        if (denominator == 0) {
            return 1.0;
        }
        return Math.round(numerator * 10000.0 / denominator) / 10000.0;
    }

    // ==================== F03~F05 分析报告 ====================

    public List<RptReportEntity> listReports(Integer reportType, String period) {
        return reportMapper.selectList(reportType, period);
    }

    /** 手动生成报告（FR-605）：聚合 → HTML 落盘（D 盘） → rpt_report 记录 */
    @Transactional
    public long generateReport(int type, String period, DataScope scope) {
        PeriodRange range = parsePeriod(type, period);
        statDailyService.ensureAggregated(range.from(), range.to());
        StatVo stats = sum(statDailyService.rangeRows(null, range.from(), range.to(), scope));

        // 趋势：区间末 7 日（不足取区间起点）
        LocalDate trendFrom = range.to().minusDays(6);
        if (trendFrom.isBefore(range.from())) {
            trendFrom = range.from();
        }
        List<Trend7d> trend = new ArrayList<>();
        for (com.tgaws.business.rpt.entity.RptStatDailyEntity r
                : statDailyService.rangeRows(null, trendFrom, range.to(), scope)) {
            trend.add(new Trend7d(r.getStatDate(), r.getWarnTotal(), rate(r.getPatrolDone(), r.getPatrolTotal())));
        }

        String html = buildHtml(typeName(type), period, range, stats, trend);
        String fileName = "rpt_" + type + "_" + period + ".html";
        Path file = Path.of(rptDir, "r" + type, fileName);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, html, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BizException(ErrorCode.D0004, "报告文件写入失败：" + e.getMessage());
        }

        RptReportEntity entity = new RptReportEntity();
        entity.setReportNo("RPT" + LocalDate.now().format(YMD)
                + String.format("%06d", idGenerator.nextId() % 1_000_000L));
        entity.setReportType(type);
        entity.setPeriod(period);
        entity.setFilePath(file.toString());
        entity.setStatus(1);
        reportMapper.insert(entity);
        log.info("分析报告生成：{}（{} {}）→ {}", entity.getReportNo(), typeName(type), period, file);
        return entity.getId();
    }

    /** 报告文件路径（下载用） */
    public String reportPath(long id) {
        RptReportEntity report = reportMapper.selectById(id);
        if (report == null || report.getStatus() != 1) {
            throw new BizException(ErrorCode.B0802);
        }
        return report.getFilePath();
    }

    // ==================== 周期解析 ====================

    /** 周期解析：日=YYYYMMDD；周=YYYYWW（ISO 周）；月=YYYYMM */
    static PeriodRange parsePeriod(int type, String period) {
        if (period == null || period.isBlank()) {
            throw new BizException(ErrorCode.A0002, "period 必填（日=YYYYMMDD 周=YYYYWW 月=YYYYMM）");
        }
        try {
            switch (type) {
                case 1 -> {
                    LocalDate d = LocalDate.parse(period, YMD);
                    return new PeriodRange(d, d);
                }
                case 2 -> {
                    int year = Integer.parseInt(period.substring(0, 4));
                    int week = Integer.parseInt(period.substring(4));
                    LocalDate monday = LocalDate.of(year, 1, 4)
                            .minusDays(LocalDate.of(year, 1, 4).getDayOfWeek().getValue() - 1L)
                            .plusWeeks(week - 1L);
                    return new PeriodRange(monday, monday.plusDays(6));
                }
                case 3 -> {
                    YearMonth ym = YearMonth.parse(period, YM);
                    return new PeriodRange(ym.atDay(1), ym.atEndOfMonth());
                }
                default -> throw new BizException(ErrorCode.A0002, "报表类型必须为 1日报/2周报/3月报");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.A0002, "period 格式错误（日=YYYYMMDD 周=YYYYWW 月=YYYYMM）");
        }
    }

    private static String typeName(int type) {
        return switch (type) {
            case 1 -> "日报";
            case 2 -> "周报";
            case 3 -> "月报";
            default -> "报告";
        };
    }

    // ==================== HTML 生成 ====================

    private String buildHtml(String typeName, String period, PeriodRange range,
                             StatVo stats, List<Trend7d> trend) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<!DOCTYPE html><html lang=\"zh\"><head><meta charset=\"UTF-8\">");
        sb.append("<title>隧道地质灾害AI预警系统监测分析").append(typeName).append(" ").append(period).append("</title>");
        sb.append("<style>body{font-family:'Microsoft YaHei',sans-serif;margin:24px;color:#222}");
        sb.append("h1{font-size:22px;border-bottom:2px solid #2c5f8a;padding-bottom:8px}");
        sb.append("table{border-collapse:collapse;margin:16px 0;width:100%;max-width:720px}");
        sb.append("td,th{border:1px solid #bbb;padding:6px 10px;font-size:14px}");
        sb.append("th{background:#eef3f8}svg{margin:12px 0}li{margin:4px 0}</style></head><body>");
        sb.append("<h1>隧道地质灾害AI预警系统 · 监测分析").append(typeName)
                .append("（").append(range.from()).append(" ~ ").append(range.to()).append("）</h1>");
        sb.append("<table><tr><th>指标</th><th>数值</th></tr>");
        row(sb, "预警总数 / 红级", stats.warnTotal() + " / " + stats.warnRed());
        row(sb, "已确认 / 已消警", stats.warnConfirmed() + " / " + stats.warnClosed());
        row(sb, "处置派单 / 已闭环（闭环率）", stats.disposeTotal() + " / " + stats.disposeClosed()
                + "（" + percent(stats.closeRate()) + "）");
        row(sb, "巡检任务 / 已完成（完成率）", stats.patrolTotal() + " / " + stats.patrolDone()
                + "（" + percent(stats.patrolRate()) + "）");
        row(sb, "新增隐患 / 闭环隐患", stats.hazardNew() + " / " + stats.hazardClosed());
        row(sb, "实收样本 / 应收样本（完整率）", stats.sampleCount() + " / " + stats.sampleExpect()
                + "（" + percent(stats.sampleRate()) + "）");
        sb.append("</table>");
        sb.append("<h2>近 7 日预警趋势</h2>").append(trendSvg(trend));
        sb.append("<h2>结论建议</h2><ul>");
        List<String> suggestions = suggestions(stats);
        for (String s : suggestions) {
            sb.append("<li>").append(s).append("</li>");
        }
        sb.append("</ul><p style=\"color:#888;font-size:12px\">本报告由系统自动生成（数据口径见《6》6.4.5）</p>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private static void row(StringBuilder sb, String label, String value) {
        sb.append("<tr><td>").append(label).append("</td><td>").append(value).append("</td></tr>");
    }

    private static String percent(double rate) {
        return String.format(Locale.ROOT, "%.1f%%", rate * 100);
    }

    /** 近 7 日预警数折线（纯 SVG，无外部依赖） */
    private static String trendSvg(List<Trend7d> trend) {
        if (trend.isEmpty()) {
            return "<p>无数据</p>";
        }
        long max = Math.max(1, trend.stream().mapToLong(Trend7d::warnTotal).max().orElse(1));
        int width = 720;
        int height = 220;
        int padLeft = 46;
        int padBottom = 26;
        double step = (double) (width - padLeft - 10) / Math.max(1, trend.size() - 1);
        StringBuilder points = new StringBuilder();
        for (int i = 0; i < trend.size(); i++) {
            double x = padLeft + i * step;
            double y = 8 + (height - padBottom - 16) * (1 - trend.get(i).warnTotal() * 1.0 / max);
            if (i > 0) {
                points.append(' ');
            }
            points.append(String.format(Locale.ROOT, "%.1f,%.1f", x, y));
        }
        StringBuilder labels = new StringBuilder();
        for (int i = 0; i < trend.size(); i++) {
            double x = padLeft + i * step;
            labels.append("<text x=\"").append(String.format(Locale.ROOT, "%.1f", x - 14))
                    .append("\" y=\"").append(height - 6).append("\" font-size=\"10\">")
                    .append(trend.get(i).date().format(DateTimeFormatter.ofPattern("MM-dd")))
                    .append("</text>");
        }
        return "<svg width=\"" + width + "\" height=\"" + height + "\" xmlns=\"http://www.w3.org/2000/svg\">"
                + "<line x1=\"" + padLeft + "\" y1=\"" + 8 + "\" x2=\"" + padLeft + "\" y2=\""
                + (height - padBottom) + "\" stroke=\"#999\"/>"
                + "<line x1=\"" + padLeft + "\" y1=\"" + (height - padBottom) + "\" x2=\"" + (width - 8)
                + "\" y2=\"" + (height - padBottom) + "\" stroke=\"#999\"/>"
                + "<text x=\"4\" y=\"12\" font-size=\"10\">" + max + "</text>"
                + "<polyline points=\"" + points + "\" fill=\"none\" stroke=\"#c0392b\" stroke-width=\"2\"/>"
                + labels + "</svg>";
    }

    private static List<String> suggestions(StatVo stats) {
        List<String> list = new ArrayList<>();
        if (stats.warnRed() > 0) {
            list.add("本期出现红级预警 " + stats.warnRed() + " 起，建议组织专项排查并复核处置闭环情况。");
        }
        if (stats.closeRate() < CLOSE_RATE_LINE) {
            list.add("处置闭环率 " + percent(stats.closeRate()) + " 低于 80%，建议督办未闭环处置任务。");
        }
        if (stats.patrolRate() < PATROL_RATE_LINE) {
            list.add("巡检完成率 " + percent(stats.patrolRate()) + " 低于 90%，建议核查巡检任务执行与逾期情况。");
        }
        if (stats.sampleRate() < SAMPLE_RATE_LINE) {
            list.add("数据完整率 " + percent(stats.sampleRate()) + " 低于 95%，建议排查网关/点位断链。");
        }
        if (list.isEmpty()) {
            list.add("本期各项指标正常：无红级预警，闭环率/巡检完成率/数据完整率均在阈值以上。");
        }
        return list;
    }

    // ==================== 出参与周期类型 ====================

    /** F02 统计报表出参 */
    public record StatVo(long warnTotal, long warnRed, long warnConfirmed, long warnClosed,
                         long disposeTotal, long disposeClosed, double closeRate,
                         long patrolTotal, long patrolDone, double patrolRate,
                         long hazardNew, long hazardClosed,
                         long sampleCount, long sampleExpect, double sampleRate) {
    }

    /** 周期范围 */
    public record PeriodRange(LocalDate from, LocalDate to) {
    }

    /** 7 日趋势点 */
    private record Trend7d(LocalDate date, long warnTotal, double patrolRate) {
    }
}
