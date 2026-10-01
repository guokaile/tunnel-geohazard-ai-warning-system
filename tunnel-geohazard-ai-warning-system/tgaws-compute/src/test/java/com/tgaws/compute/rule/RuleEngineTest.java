package com.tgaws.compute.rule;

import com.tgaws.common.enums.RuleType;
import com.tgaws.common.enums.WarnLevel;
import com.tgaws.common.rule.MonRule;
import com.tgaws.common.rule.PointSample;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规则引擎单测（《5》5.4：消抖/迟滞/速率/组合/降权计数/停用跳过）。
 */
class RuleEngineTest {

    private static final String POINT = "P00010001";
    private static final MonRule CH4_YELLOW = new MonRule("R-CH4-HI-Y", RuleType.THRESHOLD_UPPER,
            WarnLevel.YELLOW, "{\"threshold\":0.5,\"on\":3,\"hysteresisPct\":5}", 100, true);

    private final RuleEngine engine = new RuleEngine();

    @Test
    void thresholdTriggersAfterDebounce() {
        // 2 次超限不触发，第 3 次连续超限触发
        assertFalse(engine.evaluateThreshold(CH4_YELLOW, POINT, sample("0.60")).isPresent());
        assertFalse(engine.evaluateThreshold(CH4_YELLOW, POINT, sample("0.60")).isPresent());
        Optional<RuleHit> hit = engine.evaluateThreshold(CH4_YELLOW, POINT, sample("0.60"));
        assertTrue(hit.isPresent(), "连续 3 次超限应触发");
        assertEquals(WarnLevel.YELLOW, hit.get().warnLevel());
    }

    @Test
    void debounceResetOnBelowThreshold() {
        assertFalse(engine.evaluateThreshold(CH4_YELLOW, POINT, sample("0.60")).isPresent());
        assertFalse(engine.evaluateThreshold(CH4_YELLOW, POINT, sample("0.60")).isPresent());
        engine.evaluateThreshold(CH4_YELLOW, POINT, sample("0.20")); // 中断
        assertFalse(engine.evaluateThreshold(CH4_YELLOW, POINT, sample("0.60")).isPresent());
        assertFalse(engine.evaluateThreshold(CH4_YELLOW, POINT, sample("0.60")).isPresent());
        assertTrue(engine.evaluateThreshold(CH4_YELLOW, POINT, sample("0.60")).isPresent(),
                "中断后重新计数，第 3 次连续超限触发");
    }

    @Test
    void hysteresisPreventsReTriggerUntilRearm() {
        // 首次触发
        engine.evaluateThreshold(CH4_YELLOW, POINT, sample("0.60"));
        engine.evaluateThreshold(CH4_YELLOW, POINT, sample("0.60"));
        assertTrue(engine.evaluateThreshold(CH4_YELLOW, POINT, sample("0.60")).isPresent());
        // 迟滞带内回落（0.49 > 0.5×0.95=0.475）→ 不重新武装；再次超限不触发
        engine.evaluateThreshold(CH4_YELLOW, POINT, sample("0.49"));
        assertFalse(engine.evaluateThreshold(CH4_YELLOW, POINT, sample("0.60")).isPresent());
        assertFalse(engine.evaluateThreshold(CH4_YELLOW, POINT, sample("0.60")).isPresent());
        assertFalse(engine.evaluateThreshold(CH4_YELLOW, POINT, sample("0.60")).isPresent(),
                "迟滞带内不允许重复触发");
        // 回落至迟滞带以下 → 重新武装
        engine.evaluateThreshold(CH4_YELLOW, POINT, sample("0.40"));
        assertFalse(engine.evaluateThreshold(CH4_YELLOW, POINT, sample("0.60")).isPresent());
        assertFalse(engine.evaluateThreshold(CH4_YELLOW, POINT, sample("0.60")).isPresent());
        assertTrue(engine.evaluateThreshold(CH4_YELLOW, POINT, sample("0.60")).isPresent(),
                "重新武装后可再次触发");
    }

    @Test
    void rateRuleComputesMmPerDay() {
        MonRule rule = new MonRule("R-CONV-RATE-Y", RuleType.RATE, WarnLevel.YELLOW,
                "{\"rate\":3.0,\"windowHour\":24,\"unit\":\"mmPerDay\",\"on\":1}", 100, true);
        // 24h 窗口内变化 4mm → 4mm/d ≥ 3 → 触发（on=1 免消抖）
        long now = System.currentTimeMillis();
        List<PointSample> recent = List.of(
                new PointSample(now - 86_400_000L, BigDecimal.valueOf(100.0), 0),
                new PointSample(now, BigDecimal.valueOf(104.0), 0));
        Optional<RuleHit> hit = engine.evaluateRate(rule, POINT,
                new PointSample(now, BigDecimal.valueOf(104.0), 0), recent);
        assertTrue(hit.isPresent(), "4mm/d 应触发 3mm/d 速率规则");
    }

    @Test
    void lowQualitySamplesExcludedFromRateWindow() {
        MonRule rule = new MonRule("R-CONV-RATE-Y", RuleType.RATE, WarnLevel.YELLOW,
                "{\"rate\":3.0,\"windowHour\":24,\"unit\":\"mmPerDay\",\"on\":1}", 100, true);
        long now = System.currentTimeMillis();
        // 窗口首样本为跳变质量位（权重 0.5）→ 不计入 → 有效样本不足 → 不判定
        List<PointSample> recent = List.of(
                new PointSample(now - 86_400_000L, BigDecimal.valueOf(100.0), 2));
        Optional<RuleHit> hit = engine.evaluateRate(rule, POINT,
                new PointSample(now, BigDecimal.valueOf(104.0), 0), recent);
        assertFalse(hit.isPresent(), "有效样本不足时不判定（降权计数规则）");
    }

    @Test
    void combinationRuleWithAviator() {
        MonRule rule = new MonRule("R-COMB", RuleType.COMBINATION, WarnLevel.ORANGE,
                "r1 && (r2 || r3)", 90, true);
        assertTrue(engine.evaluateCombination(rule, Map.of("r1", true, "r2", true, "r3", false)).isPresent());
        assertFalse(engine.evaluateCombination(rule, Map.of("r1", false, "r2", true, "r3", true)).isPresent());
    }

    @Test
    void disabledRuleSkipped() {
        MonRule disabled = new MonRule("R-OFF", RuleType.THRESHOLD_UPPER,
                WarnLevel.RED, "{\"threshold\":0.5,\"on\":1}", 100, false);
        assertFalse(engine.evaluateThreshold(disabled, POINT, sample("9.9")).isPresent());
    }

    private static PointSample sample(String value) {
        return new PointSample(System.currentTimeMillis(), new BigDecimal(value), 0);
    }
}
