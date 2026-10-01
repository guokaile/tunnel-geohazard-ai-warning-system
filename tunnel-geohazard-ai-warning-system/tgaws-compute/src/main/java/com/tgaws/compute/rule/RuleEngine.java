package com.tgaws.compute.rule;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecode.aviator.AviatorEvaluator;
import com.tgaws.common.enums.QualityFlag;
import com.tgaws.common.enums.RuleType;
import com.tgaws.common.rule.MonRule;
import com.tgaws.common.rule.PointSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 规则引擎（《5.算法设计说明书》5.4 单指标判定）：
 *
 * <p>①阈值规则：迟滞消抖——连续 N_on 次超限才触发；触发后须回落至迟滞带以下
 * （A_on×(1-hysteresisPct/100)）才重新武装，防阈值边界抖动导致的频繁告警；
 * ②速率规则：窗口首尾差÷时间跨度，按单位换算（mm/d、%/min）；
 * ③组合规则：Aviator 表达式（r1 && (r2 || r3)）对子规则命中布尔组合；
 * ④有效样本口径：质量位权重 ≤0.5 的样本不计入窗口统计（《5》5.3 降权计数规则）。</p>
 */
public class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 迟滞回差默认值（%量程） */
    private static final double DEFAULT_HYSTERESIS_PCT = 5.0;

    /** 消抖默认次数 */
    private static final int DEFAULT_DEBOUNCE = 3;

    /** 状态：key=ruleCode#pointCode；value>0=连续超限计数，-1=已触发待重新武装 */
    private final ConcurrentHashMap<String, Integer> state = new ConcurrentHashMap<>();

    // ---------- 阈值规则 ----------

    /**
     * 阈值判定（上限/下限型，含消抖与迟滞）。
     */
    public Optional<RuleHit> evaluateThreshold(MonRule rule, String pointCode, PointSample sample) {
        if (!eligible(rule)) {
            return Optional.empty();
        }
        Map<String, Object> p = parseParams(rule.expressionJson());
        BigDecimal threshold = toDecimal(p.get("threshold"));
        int debounce = intParam(p, "on", DEFAULT_DEBOUNCE);
        double hysteresisPct = doubleParam(p, "hysteresisPct", DEFAULT_HYSTERESIS_PCT);
        boolean upperType = rule.ruleType() == RuleType.THRESHOLD_UPPER;
        boolean over = upperType
                ? sample.value().compareTo(threshold) >= 0
                : sample.value().compareTo(threshold) <= 0;
        String key = rule.ruleCode() + "#" + pointCode;
        int cur = state.getOrDefault(key, 0);
        if (over) {
            if (cur < 0) {
                return Optional.empty(); // 已触发，未重新武装（迟滞带内）
            }
            int next = cur + 1;
            if (next >= debounce) {
                state.put(key, -1); // 触发后锁定，等待回落至迟滞带以下
                return Optional.of(new RuleHit(rule.ruleCode(), rule.warnLevel(),
                        "值=" + sample.value() + " 阈值=" + threshold));
            }
            state.put(key, next);
            return Optional.empty();
        }
        // 未超限：回落判定（迟滞重新武装）
        BigDecimal rearmBoundary = upperType
                ? threshold.multiply(BigDecimal.valueOf(1 - hysteresisPct / 100.0))
                : threshold.multiply(BigDecimal.valueOf(1 + hysteresisPct / 100.0));
        boolean rearm = upperType
                ? sample.value().compareTo(rearmBoundary) <= 0
                : sample.value().compareTo(rearmBoundary) >= 0;
        if (cur < 0 && rearm) {
            state.put(key, 0); // 重新武装
        } else if (cur >= 0) {
            state.remove(key); // 连续中断
        }
        return Optional.empty();
    }

    // ---------- 速率规则 ----------

    /**
     * 速率判定（窗口首尾差 ÷ 时间跨度，单位换算 mm/d 或 %/min）。
     *
     * @param recent 最近窗口样本（时间升序；仅质量位权重 >0.5 的有效样本参与）
     */
    public Optional<RuleHit> evaluateRate(MonRule rule, String pointCode,
                                          PointSample sample, List<PointSample> recent) {
        if (!eligible(rule)) {
            return Optional.empty();
        }
        List<PointSample> effective = recent.stream()
                .filter(s -> QualityFlag.of(s.quality()).judgeWeight() > 0.5D)
                .toList();
        if (effective.isEmpty()) {
            return Optional.empty(); // 有效样本不足：本周期不判定（降权计数规则）
        }
        Map<String, Object> p = parseParams(rule.expressionJson());
        BigDecimal limit = toDecimal(p.get("rate"));
        int debounce = intParam(p, "on", DEFAULT_DEBOUNCE);
        String unit = String.valueOf(p.getOrDefault("unit", "mmPerDay"));
        PointSample first = effective.get(0);
        long deltaMs = sample.tsMs() - first.tsMs();
        if (deltaMs <= 0) {
            return Optional.empty();
        }
        BigDecimal delta = sample.value().subtract(first.value()).abs();
        // 单位换算（先乘后除，避免中间 scale 舍入吞掉小速率：
        //  4/86,400,000 在 scale 6 下舍入为 0 的精度缺陷）
        BigDecimal rate;
        if ("pctPerMin".equals(unit)) {
            rate = delta.multiply(BigDecimal.valueOf(60_000L))
                    .divide(BigDecimal.valueOf(deltaMs), 6, RoundingMode.HALF_UP);
        } else {
            // mmPerDay
            rate = delta.multiply(BigDecimal.valueOf(86_400_000L))
                    .divide(BigDecimal.valueOf(deltaMs), 6, RoundingMode.HALF_UP);
        }
        if (rate.compareTo(limit) < 0) {
            state.remove(rule.ruleCode() + "#" + pointCode);
            return Optional.empty();
        }
        String key = rule.ruleCode() + "#" + pointCode;
        int cur = state.getOrDefault(key, 0);
        if (cur < 0) {
            return Optional.empty();
        }
        int next = cur + 1;
        if (next >= debounce) {
            state.put(key, -1);
            return Optional.of(new RuleHit(rule.ruleCode(), rule.warnLevel(),
                    "速率=" + rate + " 限值=" + limit + " 单位=" + unit));
        }
        state.put(key, next);
        return Optional.empty();
    }

    // ---------- 组合规则 ----------

    /**
     * 组合规则（Aviator 表达式：r1 && (r2 || r3)，变量为子规则命中布尔）。
     */
    public Optional<RuleHit> evaluateCombination(MonRule rule, Map<String, Boolean> subHits) {
        if (!eligible(rule)) {
            return Optional.empty();
        }
        try {
            Map<String, Object> env = new HashMap<>(subHits);
            Object result = AviatorEvaluator.execute(rule.expressionJson(), env);
            if (Boolean.TRUE.equals(result)) {
                return Optional.of(new RuleHit(rule.ruleCode(), rule.warnLevel(),
                        "组合命中：" + rule.expressionJson() + " => " + subHits));
            }
        } catch (Exception e) {
            log.error("组合规则表达式执行失败：rule={} expr={}", rule.ruleCode(), rule.expressionJson(), e);
        }
        return Optional.empty();
    }

    /** 重置规则点位状态（点位停用/规则启停时调用） */
    public void reset(String ruleCode, String pointCode) {
        state.remove(ruleCode + "#" + pointCode);
    }

    // ---------- 内部 ----------

    private boolean eligible(MonRule rule) {
        if (!rule.enabled()) {
            return false;
        }
        return rule.ruleType() == RuleType.THRESHOLD_UPPER
                || rule.ruleType() == RuleType.THRESHOLD_LOWER
                || rule.ruleType() == RuleType.RATE
                || rule.ruleType() == RuleType.COMBINATION;
    }

    private Map<String, Object> parseParams(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("规则参数 JSON 非法（B0602）：" + json, e);
        }
    }

    private static BigDecimal toDecimal(Object v) {
        if (v == null) {
            throw new IllegalArgumentException("规则参数缺失（B0602）");
        }
        return new BigDecimal(String.valueOf(v));
    }

    private static int intParam(Map<String, Object> p, String key, int def) {
        Object v = p.get(key);
        return v == null ? def : ((Number) v).intValue();
    }

    private static double doubleParam(Map<String, Object> p, String key, double def) {
        Object v = p.get(key);
        return v == null ? def : ((Number) v).doubleValue();
    }
}
