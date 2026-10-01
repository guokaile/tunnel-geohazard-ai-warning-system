package com.tgaws.compute.judge;

import com.tgaws.common.enums.RuleType;
import com.tgaws.common.enums.WarnLevel;
import com.tgaws.common.rule.MonRule;
import com.tgaws.common.store.SampleRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 点位判定管理器单测（compute 纯判定：规则命中/突变/异常，不碰库不产生事件）。
 */
class PointJudgeManagerTest {

    private static final MonRule CH4_YELLOW = new MonRule("R-CH4-HI-Y", RuleType.THRESHOLD_UPPER,
            WarnLevel.YELLOW, "{\"threshold\":0.5,\"on\":3,\"hysteresisPct\":5}", 100, true);

    @Test
    void thresholdRuleHitViaManager() {
        PointJudgeManager manager = new PointJudgeManager(1000, 100);
        PointJudgeManager.Judgment judgment = null;
        for (int i = 0; i < 3; i++) {
            judgment = manager.onSample(row(1L, "0.60"), List.of(CH4_YELLOW));
        }
        assertTrue(judgment != null && !judgment.ruleHits().isEmpty(), "连续 3 次超限应命中规则");
        assertTrue(judgment.ruleHits().get(0).warnLevel() == WarnLevel.YELLOW);
        assertFalse(judgment.mutationHit(), "小样本量 CUSUM 冷启动不判定");
    }

    @Test
    void mutationDetectedWithSmallColdStart() {
        Random rnd = new Random(5);
        PointJudgeManager manager = new PointJudgeManager(30, 100);
        for (int i = 0; i < 60; i++) {
            manager.onSample(row(2L, String.valueOf(10.0D + rnd.nextGaussian())), List.of());
        }
        // 5σ 阶跃
        boolean hit = false;
        for (int i = 0; i < 20 && !hit; i++) {
            hit = manager.onSample(row(2L, String.valueOf(15.0D + rnd.nextGaussian())), List.of()).mutationHit();
        }
        assertTrue(hit, "5σ 阶跃应被 CUSUM 检出（minSamples=30 快速暖机）");
    }

    @Test
    void outlierFlaggedNotFedToFilter() {
        Random rnd = new Random(6);
        PointJudgeManager manager = new PointJudgeManager(30, 100);
        for (int i = 0; i < 100; i++) {
            manager.onSample(row(3L, String.valueOf(10.0D + rnd.nextGaussian())), List.of());
        }
        boolean outlier = manager.onSample(row(3L, "25.0"), List.of()).outlier();
        assertTrue(outlier, "远离中位数的极端值应标记为异常（不直接告警）");
    }

    @Test
    void refreshBaselinesNoOpOnEmptyStates() {
        PointJudgeManager manager = new PointJudgeManager(30, 100);
        manager.refreshBaselines();
        assertTrue(manager.stateCount() == 0);
    }

    private static SampleRow row(long pointId, String value) {
        return new SampleRow(pointId, System.currentTimeMillis(), new BigDecimal(value), 0, 1);
    }
}
