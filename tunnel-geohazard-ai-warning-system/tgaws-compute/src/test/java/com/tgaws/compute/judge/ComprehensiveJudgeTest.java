package com.tgaws.compute.judge;

import com.tgaws.common.enums.WarnLevel;
import com.tgaws.compute.judge.ComprehensiveJudge.Indicator;
import com.tgaws.compute.judge.ComprehensiveJudge.JudgeResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 综合研判单测（T-504 验收：《5》附录C 标定表两例逐格复算 + 评审联立校验两例）。
 *
 * <p>限值基线（附录C）：瓦斯 CH₄ A_safe=0.2/A_warn=0.5/A_lim=1.5、速率 0.01/0.1/0.3 %/min、
 * 风速（下限型）1.0/0.5/0.25 m/s；坍塌 速率 1/3/8 mm/d、累计 30/60/120 mm、
 * 轴力/应力占比 0.5/0.8/1.0。权重：瓦斯 0.50/0.30/0.20；坍塌 0.35/0.25/0.20/0.20。</p>
 */
class ComprehensiveJudgeTest {

    private static final double EPS = 1e-9;

    private final ComprehensiveJudge judge = new ComprehensiveJudge();

    // ---------- 附录C 瓦斯例（权重 0.50/0.30/0.20） ----------

    @Test
    void gasNormalNoAlarm() {
        JudgeResult r = judge.judge(List.of(
                gasCh4(0.2D, 0.50D), gasRate(0.01D, 0.30D), gasWind(1.0D, 0.20D)));
        assertEquals(0.0D, r.score(), EPS);
        assertNull(r.level(), "正常工况不预警");
    }

    @Test
    void gasOrangeCalibrationCaseFromReview() {
        // 评审例1：CH₄=1.0%、速率=0.12%/min、风速正常 → 期望橙级
        JudgeResult r = judge.judge(List.of(
                gasCh4(1.0D, 0.50D), gasRate(0.12D, 0.30D), gasWind(1.0D, 0.20D)));
        // 逐格：g(CH₄)=0.5+0.5×(1.0-0.5)/(1.5-0.5)=0.75；g(rate)=0.5+0.5×0.02/0.2=0.55；g(wind)=0
        assertEquals(0.75D, ComprehensiveJudge.gValue(gasCh4(1.0D, 1.0D)), EPS);
        assertEquals(0.55D, ComprehensiveJudge.gValue(gasRate(0.12D, 1.0D)), EPS);
        assertEquals(0.0D, ComprehensiveJudge.gValue(gasWind(1.0D, 1.0D)), EPS);
        assertEquals(0.5D * 0.75D + 0.3D * 0.55D, r.score(), EPS);
        assertEquals(WarnLevel.ORANGE, r.level(), "联立标定：瓦斯典型橙级工况必须落橙级");
    }

    @Test
    void gasOrangeAppendixCase() {
        // 附录C 原例：1.0%/0.15%/风速0.6 → S=0.616
        JudgeResult r = judge.judge(List.of(
                gasCh4(1.0D, 0.50D), gasRate(0.15D, 0.30D), gasWind(0.6D, 0.20D)));
        assertEquals(0.6158D, r.score(), 1e-4, "0.375+0.1875+0.0533=0.6158");
        assertEquals(WarnLevel.ORANGE, r.level());
    }

    @Test
    void gasRedCase() {
        JudgeResult r = judge.judge(List.of(
                gasCh4(1.5D, 0.50D), gasRate(0.3D, 0.30D), gasWind(0.4D, 0.20D)));
        assertEquals(0.9400D, r.score(), 1e-4, "1.0+1.0+0.7 加权 = 0.94");
        assertEquals(WarnLevel.RED, r.level());
    }

    @Test
    void singleIndicatorAtRedLimitRatedOrangeByJudgeOnly() {
        // 单指标红限（CH₄=1.5%，其余正常）：S=0.5×1.0=0.5 → 综合研判橙；
        // 规则通道直接定红，取严=红（5.7.1 设计，测试记录该口径）
        JudgeResult r = judge.judge(List.of(
                gasCh4(1.5D, 0.50D), gasRate(0.01D, 0.30D), gasWind(1.0D, 0.20D)));
        assertEquals(0.50D, r.score(), EPS);
        assertEquals(WarnLevel.ORANGE, r.level());
    }

    // ---------- 附录C 坍塌例（权重 0.35/0.25/0.20/0.20） ----------

    @Test
    void collapseOrangeCalibrationCaseFromReview() {
        // 评审例2：位移速率+锚杆轴力双超限 → 期望橙级
        // 速率=5mm/d→g=0.7；累计=60mm→g=0.167；轴力=1.0→g=1.0；应力=0.7→g=0.2
        JudgeResult r = judge.judge(List.of(
                collapseRate(5.0D, 0.35D), collapseCum(60.0D, 0.25D),
                collapseAxial(1.0D, 0.20D), collapseStress(0.7D, 0.20D)));
        assertEquals(0.35D * 0.7D + 0.25D * 0.1666666667D + 0.20D * 1.0D + 0.20D * 0.2D,
                r.score(), 1e-4);
        assertEquals(WarnLevel.ORANGE, r.level(), "联立标定：坍塌双超限工况必须落橙级");
    }

    @Test
    void collapseOrangeAppendixCase() {
        // 附录C 原例：5mm/d/100mm/轴力1.0/应力0.9 → S=0.803
        JudgeResult r = judge.judge(List.of(
                collapseRate(5.0D, 0.35D), collapseCum(100.0D, 0.25D),
                collapseAxial(1.0D, 0.20D), collapseStress(0.9D, 0.20D)));
        assertEquals(0.8033D, r.score(), 1e-3);
        assertEquals(WarnLevel.ORANGE, r.level());
    }

    @Test
    void collapseRedCase() {
        JudgeResult r = judge.judge(List.of(
                collapseRate(8.0D, 0.35D), collapseCum(120.0D, 0.25D),
                collapseAxial(1.0D, 0.20D), collapseStress(1.0D, 0.20D)));
        assertEquals(1.0D, r.score(), EPS);
        assertEquals(WarnLevel.RED, r.level());
    }

    @Test
    void lowerSideMirror() {
        // 下限型镜像：风速 0.4 m/s（warn=0.5/lim=0.25）→ g=0.5+0.5×(0.5-0.4)/(0.5-0.25)=0.7
        assertEquals(0.7D, ComprehensiveJudge.gValue(gasWind(0.4D, 1.0D)), EPS);
        // 风速 0.2（低于下限）→ g=1.0
        assertEquals(1.0D, ComprehensiveJudge.gValue(gasWind(0.2D, 1.0D)), EPS);
    }

    // ---------- 指标构造（权重参数仅用于 judge，gValue 单测传 1.0 占位） ----------

    private static ComprehensiveJudge.Indicator gasCh4(double v, double w) {
        return new ComprehensiveJudge.Indicator(301, v, 0.2D, 0.5D, 1.5D, true, w);
    }

    private static ComprehensiveJudge.Indicator gasRate(double v, double w) {
        return new ComprehensiveJudge.Indicator(304, v, 0.01D, 0.1D, 0.3D, true, w);
    }

    private static ComprehensiveJudge.Indicator gasWind(double v, double w) {
        return new ComprehensiveJudge.Indicator(304, v, 1.0D, 0.5D, 0.25D, false, w);
    }

    private static ComprehensiveJudge.Indicator collapseRate(double v, double w) {
        return new ComprehensiveJudge.Indicator(601, v, 1.0D, 3.0D, 8.0D, true, w);
    }

    private static ComprehensiveJudge.Indicator collapseCum(double v, double w) {
        return new ComprehensiveJudge.Indicator(501, v, 30.0D, 60.0D, 120.0D, true, w);
    }

    private static ComprehensiveJudge.Indicator collapseAxial(double v, double w) {
        return new ComprehensiveJudge.Indicator(102, v, 0.5D, 0.8D, 1.0D, true, w);
    }

    private static ComprehensiveJudge.Indicator collapseStress(double v, double w) {
        return new ComprehensiveJudge.Indicator(103, v, 0.5D, 0.8D, 1.0D, true, w);
    }
}
