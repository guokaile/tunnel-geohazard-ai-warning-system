package com.tgaws.compute.forecast;

import com.tgaws.common.rule.PointSample;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 趋势预测单测（《5》5.5.1 模型选择：冷启动/波动-EWMA/趋势-回归/缓变-Holt）。
 */
class ForecastServiceTest {

    /** 24h 分钟样本 */
    private static final int MIN_TRAIN = 1440;

    /** 7d 分钟样本 */
    private static final int MIN_TREND = 10080;

    private final ForecastService service = new ForecastService(MIN_TRAIN, MIN_TREND);

    @Test
    void coldStartReturnsEmpty() {
        List<PointSample> samples = samples(MIN_TRAIN - 1, i -> 10.0D);
        assertTrue(service.forecast(1L, System.currentTimeMillis(), samples).isEmpty(),
                "样本 <24h 不启用预测（防模型噪声）");
    }

    @Test
    void volatileSeriesSelectsEwma() {
        Random rnd = new Random(1);
        List<PointSample> samples = samples(MIN_TRAIN, i -> 10.0D + rnd.nextGaussian() * 5.0D);
        Optional<ForecastService.ForecastResult> r =
                service.forecast(1L, System.currentTimeMillis(), samples);
        assertTrue(r.isPresent());
        assertEquals(ForecastModel.EWMA, r.get().model(), "σ/μ>0.3 高波动 → EWMA");
        assertEquals(6, r.get().points().size(), "30min 时域 5min 步长 = 6 点");
        // 平滑值应接近均值（远离极端值）
        double mean = samples.stream().mapToDouble(s -> s.value().doubleValue()).average().orElse(0);
        assertEquals(mean, r.get().points().get(0).value().doubleValue(), 2.0D, "EWMA 外推接近均值");
    }

    @Test
    void trendSeriesSelectsLinearAndFits() {
        Random rnd = new Random(2);
        // 线性趋势 y=10+0.01×i + 小噪声（趋势显著）
        List<PointSample> samples = samples(MIN_TREND, i -> 10.0D + 0.01D * i + rnd.nextGaussian() * 0.05D);
        Optional<ForecastService.ForecastResult> r =
                service.forecast(1L, System.currentTimeMillis(), samples);
        assertTrue(r.isPresent());
        assertEquals(ForecastModel.LINEAR, r.get().model(), "≥7d 且趋势显著 → 线性回归");
        // 首点预测值应接近真实外推 10+0.01×(n-1+5)
        double expected = 10.0D + 0.01D * (MIN_TREND - 1 + 5);
        ForecastService.ForecastPoint first = r.get().points().get(0);
        assertEquals(expected, first.value().doubleValue(), 0.15D, "回归外推接近真实趋势");
        assertTrue(first.upper().compareTo(first.value()) > 0, "置信区间上界 > 预测值");
        assertTrue(first.lower().compareTo(first.value()) < 0, "置信区间下界 < 预测值");
    }

    @Test
    void calmSeriesSelectsHolt() {
        Random rnd = new Random(3);
        // 平缓缓变 + 小噪声（趋势不显著）
        List<PointSample> samples = samples(MIN_TREND, i -> 20.0D + rnd.nextGaussian() * 0.01D);
        Optional<ForecastService.ForecastResult> r =
                service.forecast(1L, System.currentTimeMillis(), samples);
        assertTrue(r.isPresent());
        assertEquals(ForecastModel.HOLT, r.get().model(), "缓变且趋势不显著 → Holt");
        assertEquals(20.0D, r.get().points().get(0).value().doubleValue(), 0.1D, "Holt 外推接近水平");
    }

    private static List<PointSample> samples(int n, java.util.function.IntToDoubleFunction gen) {
        List<PointSample> list = new ArrayList<>(n);
        long base = System.currentTimeMillis() - n * 60_000L;
        for (int i = 0; i < n; i++) {
            list.add(new PointSample(base + i * 60_000L, BigDecimal.valueOf(gen.applyAsDouble(i)), 0));
        }
        return list;
    }
}
