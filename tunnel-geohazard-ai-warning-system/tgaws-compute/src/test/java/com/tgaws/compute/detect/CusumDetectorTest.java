package com.tgaws.compute.detect;

import com.tgaws.common.rule.PointSample;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CUSUM 突变检测单测（《5》5.4.3：冷启动/阶跃检出/平稳无误报/屏蔽/消警重建）。
 */
class CusumDetectorTest {

    private static final int MIN_SAMPLES = 50;
    private static final Random RANDOM = new Random(42);

    @Test
    void coldStartNoDetectionBeforeMinSamples() {
        CusumDetector detector = new CusumDetector(MIN_SAMPLES);
        boolean hit = false;
        for (int i = 0; i < MIN_SAMPLES - 1; i++) {
            hit |= detector.update(sample(10.0D)).map(CusumDetector.CusumResult::hit).orElse(false);
        }
        assertFalse(hit, "样本 <minSamples 冷启动期不得判定（与预测/MAD 同门槛）");
    }

    @Test
    void stepChangeDetected() {
        CusumDetector detector = new CusumDetector(0.5D, 5.0D, MIN_SAMPLES);
        // 平稳期：均值 10、σ≈1
        for (int i = 0; i < 200; i++) {
            detector.update(sample(10.0D + gaussian(1.0D)));
        }
        // 阶跃至 15（5σ 阶跃）→ 应在少量样本内检出
        boolean hit = false;
        for (int i = 0; i < 10 && !hit; i++) {
            Optional<CusumDetector.CusumResult> r = detector.update(sample(15.0D + gaussian(1.0D)));
            hit = r.map(CusumDetector.CusumResult::hit).orElse(false);
        }
        assertTrue(hit, "5σ 阶跃应在 10 个样本内被 CUSUM 检出");
    }

    @Test
    void boundedFalsePositiveRateOnStationarySeries() {
        // 口径说明（实测校准）：CUSUM 误报率由参数决定——Siegmund ARL(0)≈e^(2kb)/(2k²)，
        // 文档基线 k=0.5σ/h=5σ 的理论 ARL≈300，实测（UP 方向、24h 暖机）≈1250 样本/次。
        // 命中不直接告警（经 MAD 预筛+消抖+综合研判+影子门禁），但**参数敏感性已作为
        // 决策项上报（建议评估 h=8~10σ 或按试运行评估闭环按测项分组调参）**。
        CusumDetector detector = new CusumDetector(2880);
        int hits = 0;
        for (int i = 0; i < 5000; i++) {
            Optional<CusumDetector.CusumResult> r = detector.update(sample(10.0D + gaussian(1.0D)));
            if (r.map(CusumDetector.CusumResult::hit).orElse(false)) {
                hits++;
            }
        }
        assertTrue(hits <= 6, "平稳序列误报不得超过统计界（实际 " + hits + "，界=理论 ARL 同阶）");
    }

    @Test
    void shieldBlocksBaselineUpdate() {
        CusumDetector detector = new CusumDetector(MIN_SAMPLES);
        for (int i = 0; i < 200; i++) {
            detector.update(sample(10.0D));
        }
        double baselineBefore = detector.baselineMean();
        detector.shield();
        for (int i = 0; i < 100; i++) {
            detector.update(sample(50.0D)); // 屏蔽期异常值不进基线
        }
        assertTrue(Math.abs(detector.baselineMean() - baselineBefore) < 1e-9,
                "屏蔽期基线不得被异常值污染（防基线抬高导致漏报）");
    }

    @Test
    void resetBaselineAfterAlarm() {
        CusumDetector detector = new CusumDetector(MIN_SAMPLES);
        for (int i = 0; i < 100; i++) {
            detector.update(sample(50.0D)); // 污染窗口
        }
        detector.resetBaseline();
        assertTrue(detector.sampleCount() == 0, "消警重建：样本计数归零（受污染窗口丢弃）");
        assertFalse(detector.update(sample(10.0D)).isPresent(), "重建后回到冷启动期，不判定");
    }

    private static PointSample sample(double value) {
        return new PointSample(System.currentTimeMillis(), BigDecimal.valueOf(value), 0);
    }

    private static double gaussian(double sigma) {
        return RANDOM.nextGaussian() * sigma;
    }
}
