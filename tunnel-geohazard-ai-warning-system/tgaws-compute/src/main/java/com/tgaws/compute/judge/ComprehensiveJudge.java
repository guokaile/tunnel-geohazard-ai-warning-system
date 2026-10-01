package com.tgaws.compute.judge;

import com.tgaws.common.enums.WarnLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 多源综合研判（《5.算法设计说明书》5.6 按距限值归一化，公式 F9/F12）：
 *
 * <p>S = Σ wᵢ·gᵢ(xᵢ)，g 为"距危险限值的归一化占比"（F12 分段映射：
 * x≤A_warn→0.5·clamp(ratio,0,1)；A_warn&lt;x≤A_lim→0.5+0.5·(x−A_warn)/(A_lim−A_warn)；
 * x&gt;A_lim→1.0；下限型镜像）。</p>
 *
 * <p>分级映射（2026-09-27 联立标定修正，与附录C 标定表逐格一致）：
 * S≥0.85 红 / ≥0.50 橙 / ≥0.35 黄 / ≥0.20 蓝 / &lt;0.20 不预警。
 * 红阈值 0.85 为"近红多指标"工况（S≈0.80）留橙级档位，避免与规则通道红级混淆。
 * 单指标定级由规则通道负责，综合研判定位联合确认与升级，两者取严（5.7.1）。</p>
 */
public class ComprehensiveJudge {

    private static final Logger log = LoggerFactory.getLogger(ComprehensiveJudge.class);

    /** 分级映射（联立标定后，见《5》5.6.3） */
    public static final double BLUE_THRESHOLD = 0.20D;
    public static final double YELLOW_THRESHOLD = 0.35D;
    public static final double ORANGE_THRESHOLD = 0.50D;
    public static final double RED_THRESHOLD = 0.85D;

    /**
     * 综合研判。
     *
     * @param indicators 指标列表（每项：数值 + 限值 + 权重；权重和须=1）
     * @return 研判结果（风险分 + 建议级别；分数 <蓝阈 时级别为 null）
     */
    public JudgeResult judge(List<Indicator> indicators) {
        double score = 0D;
        StringBuilder detail = new StringBuilder();
        for (Indicator indicator : indicators) {
            double g = gValue(indicator);
            double contribution = indicator.weight() * g;
            score += contribution;
            detail.append(indicator.itemType())
                    .append(":g=").append(round4(g))
                    .append(",w=").append(indicator.weight())
                    .append(",贡献=").append(round4(contribution))
                    .append("; ");
        }
        WarnLevel level = mapLevel(score);
        if (log.isDebugEnabled()) {
            log.debug("综合研判 S={} level={} detail={}", round4(score), level, detail);
        }
        return new JudgeResult(round4(score), level, detail.toString());
    }

    /** F12：距限值归一化分段映射（上限型/下限型） */
    static double gValue(Indicator indicator) {
        double x = indicator.value();
        double safe = indicator.safe();
        double warn = indicator.warn();
        double limit = indicator.limit();
        if (indicator.upperType()) {
            double ratio = (x - safe) / (limit - safe);
            if (x <= warn) {
                return 0.5D * clamp(ratio, 0D, 1D);
            }
            if (x <= limit) {
                return 0.5D + 0.5D * (x - warn) / (limit - warn);
            }
            return 1.0D;
        }
        // 下限型镜像（值越低越危险）
        double ratio = (safe - x) / (safe - limit);
        if (x >= warn) {
            return 0.5D * clamp(ratio, 0D, 1D);
        }
        if (x >= limit) {
            return 0.5D + 0.5D * (warn - x) / (warn - limit);
        }
        return 1.0D;
    }

    private static WarnLevel mapLevel(double score) {
        if (score >= RED_THRESHOLD) {
            return WarnLevel.RED;
        }
        if (score >= ORANGE_THRESHOLD) {
            return WarnLevel.ORANGE;
        }
        if (score >= YELLOW_THRESHOLD) {
            return WarnLevel.YELLOW;
        }
        if (score >= BLUE_THRESHOLD) {
            return WarnLevel.BLUE;
        }
        return null;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double round4(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 研判指标（调用方按测项配置 safe/warn/limit 限值与权重）。
     *
     * @param itemType  测项编码（ItemType.value）
     * @param value     当前值
     * @param safe      正常基线 A_safe
     * @param warn      黄色阈值 A_warn
     * @param limit     红色限值 A_lim
     * @param upperType 上限型（越大越危险）
     * @param weight    权重 wᵢ（同组指标权重和=1）
     */
    public record Indicator(int itemType, double value, double safe, double warn, double limit,
                            boolean upperType, double weight) {
    }

    /**
     * 研判结果。
     *
     * @param score 风险分 S
     * @param level 建议级别（<蓝阈 为 null）
     * @param detail 逐指标贡献明细（snapshot 留痕）
     */
    public record JudgeResult(double score, WarnLevel level, String detail) {
    }
}
