package com.tgaws.compute.detect;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MAD 稳健异常检测单测（《5》5.3 + 评审要求：冷启动门槛与 CUSUM 一致）。
 */
class MaddOutlierFilterTest {

    private static final int MIN_SAMPLES = 50;
    private static final Random RANDOM = new Random(7);

    @Test
    void coldStartGateBlocksJudgment() {
        MaddOutlierFilter filter = new MaddOutlierFilter(MIN_SAMPLES);
        for (int i = 0; i < MIN_SAMPLES - 1; i++) {
            filter.update(10.0D + RANDOM.nextGaussian());
        }
        assertFalse(filter.isOutlier(999.0D), "冷启动期分位数不可靠，一律不判定（防初期误报）");
    }

    @Test
    void extremeValueFlaggedAsOutlier() {
        MaddOutlierFilter filter = new MaddOutlierFilter(MIN_SAMPLES);
        for (int i = 0; i < 500; i++) {
            filter.update(10.0D + RANDOM.nextGaussian());
        }
        assertTrue(filter.isOutlier(30.0D), "远离中位数的极端值应判为异常");
        assertFalse(filter.isOutlier(10.5D), "正常波动不应判为异常");
    }

    @Test
    void robustAgainstOutlierContamination() {
        // MAD 稳健性：混入 5% 极端值后，正常样本仍不误判
        MaddOutlierFilter filter = new MaddOutlierFilter(MIN_SAMPLES);
        for (int i = 0; i < 1000; i++) {
            double x = i % 20 == 0 ? 50.0D : 10.0D + RANDOM.nextGaussian();
            filter.update(x);
        }
        assertFalse(filter.isOutlier(11.0D), "MAD 对污染稳健，正常样本不得误判");
        assertTrue(filter.isOutlier(50.0D), "极端值仍应检出");
    }
}
