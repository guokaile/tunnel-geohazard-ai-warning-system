package com.tgaws.compute.judge;

import com.tgaws.common.enums.WarnLevel;
import com.tgaws.common.rule.MonRule;
import com.tgaws.common.store.SampleRow;
import com.tgaws.compute.detect.CusumDetector;
import com.tgaws.compute.detect.MaddOutlierFilter;
import com.tgaws.common.rule.PointSample;
import com.tgaws.compute.rule.RuleEngine;
import com.tgaws.compute.rule.RuleHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 点位判定管理器（《6》6.3.2 双通道判定编排，compute 纯判定层）：
 *
 * <p>职责边界（评审 3.1）：本类只输出判定结论（规则命中/突变/异常），
 * **不碰数据库、不产生事件**——定级取严、去重抑制（B0306）、事件生成与状态机
 * 归 business 的预警编排（WarnJudgeService）。</p>
 *
 * <p>每点位状态：CUSUM 检测器 + MAD 异常滤波器 + 速率规则窗口（Deque）。</p>
 */
public class PointJudgeManager {

    private static final Logger log = LoggerFactory.getLogger(PointJudgeManager.class);

    /** 默认 CUSUM/MAD 冷启动门槛（24h 样本量，30s 频率=2880；调用方可按频率换算覆盖） */
    public static final int DEFAULT_MIN_SAMPLES = 2880;

    /** 速率规则窗口默认容量 */
    public static final int DEFAULT_WINDOW_CAPACITY = 3000;

    private final int minSamples;
    private final int windowCapacity;
    private final RuleEngine ruleEngine = new RuleEngine();
    private final ConcurrentHashMap<Long, PointState> states = new ConcurrentHashMap<>();

    public PointJudgeManager() {
        this(DEFAULT_MIN_SAMPLES, DEFAULT_WINDOW_CAPACITY);
    }

    public PointJudgeManager(int minSamples, int windowCapacity) {
        this.minSamples = minSamples;
        this.windowCapacity = windowCapacity;
    }

    /**
     * 样本判定（每入库样本调用；规则由调用方按测项提供）。
     *
     * @param row   入库样本
     * @param rules 该点位匹配的启用规则（IRuleProvider 提供）
     * @return 判定结论（纯输出）
     */
    public Judgment onSample(SampleRow row, List<MonRule> rules) {
        PointState state = states.computeIfAbsent(row.pointId(),
                k -> new PointState(minSamples, windowCapacity));
        PointSample sample = new PointSample(row.tsMs(), row.value(), row.quality());
        // 1) MAD 异常判定（先判后喂：异常样本不进滤波器）
        boolean outlier = state.mad.isOutlier(sample.value().doubleValue());
        if (!outlier) {
            state.mad.update(sample.value().doubleValue());
        }
        // 2) CUSUM 突变判定（任何样本都进：检测器自行处理冷启动）
        Optional<CusumDetector.CusumResult> cusum = state.cusum.update(sample);
        boolean mutationHit = cusum.map(CusumDetector.CusumResult::hit).orElse(false);
        // 3) 规则判定（阈值/速率，消抖迟滞状态内聚于 RuleEngine）
        List<RuleHit> ruleHits = new ArrayList<>();
        for (MonRule rule : rules) {
            if (rule == null || !rule.enabled()) {
                continue;
            }
            switch (rule.ruleType()) {
                case THRESHOLD_UPPER, THRESHOLD_LOWER ->
                        ruleEngine.evaluateThreshold(rule, String.valueOf(row.pointId()), sample)
                                .ifPresent(ruleHits::add);
                case RATE -> ruleEngine.evaluateRate(rule, String.valueOf(row.pointId()), sample,
                                List.copyOf(state.window))
                        .ifPresent(ruleHits::add);
                case COMBINATION -> { /* 组合规则需同断面多测项命中集合，由 business 编排调用 evaluateCombination */ }
                default -> { }
            }
        }
        // 4) 窗口维护（速率规则输入；质量位权重 ≤0.5 的样本同样入窗，RuleEngine 内过滤）
        state.window.addLast(sample);
        while (state.window.size() > windowCapacity) {
            state.window.removeFirst();
        }
        return new Judgment(row.pointId(), ruleHits, mutationHit, outlier,
                cusum.map(CusumDetector.CusumResult::cumsum).orElse(0D));
    }

    /** 组合规则判定（business 编排同断面多测项命中集合后调用） */
    public Optional<RuleHit> evaluateCombination(MonRule rule, Map<String, Boolean> subHits) {
        return ruleEngine.evaluateCombination(rule, subHits);
    }

    /** 基线刷新（web 调度每 6h 驱动，评审 3.4：定时器归 web，compute 只暴露方法） */
    public void refreshBaselines() {
        states.values().forEach(s -> s.cusum.refreshBaseline());
        log.info("CUSUM 基线刷新完成：{} 个点位", states.size());
    }

    /** 消警重建（business 消警时调用） */
    public void resetBaseline(long pointId) {
        PointState state = states.get(pointId);
        if (state != null) {
            state.cusum.resetBaseline();
        }
    }

    /**
     * 判定结论（compute 纯输出，供 business 定级取严/去重/事件生成）。
     *
     * @param pointId     点位 id
     * @param ruleHits    规则命中列表（阈值/速率）
     * @param mutationHit CUSUM 突变命中（级别由该点 mutation 类规则决定，business 定级）
     * @param outlier     MAD 异常标记（降权/人工标记依据，不直接告警）
     * @param cumsum      当前累积和绝对值（snapshot 留痕）
     */
    public record Judgment(long pointId, List<RuleHit> ruleHits, boolean mutationHit,
                           boolean outlier, double cumsum) {

        /** 判定来源最高级别（规则命中取最高；突变命中的级别由 business 结合规则表定级） */
        public Optional<WarnLevel> highestRuleLevel() {
            return ruleHits.stream().map(RuleHit::warnLevel).max(Enum::compareTo);
        }
    }

    /** 每点位状态 */
    private static final class PointState {

        final CusumDetector cusum;
        final MaddOutlierFilter mad;
        final Deque<PointSample> window = new ArrayDeque<>();

        PointState(int minSamples, int windowCapacity) {
            this.cusum = new CusumDetector(minSamples);
            this.mad = new MaddOutlierFilter(minSamples);
        }
    }

    /** 供测试与诊断的注册点数 */
    public int stateCount() {
        return states.size();
    }
}
