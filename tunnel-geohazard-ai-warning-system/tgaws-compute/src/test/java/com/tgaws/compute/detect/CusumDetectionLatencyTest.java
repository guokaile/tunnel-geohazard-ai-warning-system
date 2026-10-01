package com.tgaws.compute.detect;

import com.tgaws.common.rule.PointSample;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * h ↔ 检出延迟对照（2026-09-27 参数决策的安全网验证）：
 *
 * <pre>
 * 合成序列：2880 样本平稳暖机（μ=10, σ=1）→ 突变注入
 * 场景一：阶跃 +1σ（x=11）
 * 场景二：缓慢漂移 0.2σ/样本（x=10+0.2i，模拟"缓慢起步的异常"）
 *
 * h=8σ 时 1σ 阶跃理论延迟约 6~15 样本（30s 采样=3~8 分钟），
 * 断言：检出延迟 ≤ 60 样本（=30 分钟 @30s），满足 NFR-A3 平均提前量 ≥15min 的算法侧基线。
 * </pre>
 */
class CusumDetectionLatencyTest {

    private static final int WARMUP = 2880;

    /** 断言上限：30 分钟 @30s 采样 */
    private static final int MAX_LATENCY_SAMPLES = 60;

    @Test
    void h8StepOneSigmaDetectedWithin30Minutes() {
        int latency = detectStepLatency(8.0D);
        System.out.println("对照表[h=8] 1σ 阶跃检出延迟 = " + latency + " 样本");
        assertTrue(latency >= 1 && latency <= MAX_LATENCY_SAMPLES,
                "h=8σ 下 1σ 阶跃应在 60 样本内检出，实际 " + latency);
    }

    @Test
    void h8SlowDriftDetectedWithin30Minutes() {
        int latency = detectDriftLatency(8.0D, 0.2D);
        System.out.println("对照表[h=8] 0.2σ/样本漂移检出延迟 = " + latency + " 样本");
        assertTrue(latency >= 1 && latency <= MAX_LATENCY_SAMPLES,
                "h=8σ 下 0.2σ/样本漂移应在 60 样本内检出，实际 " + latency);
    }

    @Test
    void latencyComparisonTable() {
        // h=5/6/7/8/10 对照表（写入测试报告：h↔检出延迟权衡依据）
        for (double h : new double[]{5.0D, 6.0D, 7.0D, 8.0D, 10.0D}) {
            int step = detectStepLatency(h);
            int drift = detectDriftLatency(h, 0.2D);
            System.out.println("对照表 h=" + h + "σ：1σ 阶跃 " + step + " 样本 / 0.2σ漂移 " + drift + " 样本");
        }
    }

    // ---------- 辅助 ----------

    private int detectStepLatency(double hSigma) {
        CusumDetector detector = new CusumDetector(0.5D, hSigma, WARMUP);
        Random rnd = new Random(11);
        for (int i = 0; i < WARMUP; i++) {
            detector.update(sample(10.0D + rnd.nextGaussian()));
        }
        for (int i = 1; i <= 500; i++) {
            if (detector.update(sample(11.0D + rnd.nextGaussian()))
                    .map(CusumDetector.CusumResult::hit).orElse(false)) {
                return i;
            }
        }
        return -1;
    }

    private int detectDriftLatency(double hSigma, double driftPerSample) {
        CusumDetector detector = new CusumDetector(0.5D, hSigma, WARMUP);
        Random rnd = new Random(12);
        for (int i = 0; i < WARMUP; i++) {
            detector.update(sample(10.0D + rnd.nextGaussian()));
        }
        for (int i = 1; i <= 500; i++) {
            double x = 10.0D + driftPerSample * i + rnd.nextGaussian() * 0.5D;
            if (detector.update(sample(x)).map(CusumDetector.CusumResult::hit).orElse(false)) {
                return i;
            }
        }
        return -1;
    }

    private static PointSample sample(double value) {
        return new PointSample(System.currentTimeMillis(), BigDecimal.valueOf(value), 0);
    }
}
