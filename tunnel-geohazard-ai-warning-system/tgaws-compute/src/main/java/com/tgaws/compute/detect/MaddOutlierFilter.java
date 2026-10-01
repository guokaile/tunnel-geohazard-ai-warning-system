package com.tgaws.compute.detect;

/**
 * MAD 稳健异常检测（《5.算法设计说明书》5.3，公式 F5）：
 * |x − median| > 3 × 1.4826 × MAD 判为异常点。
 *
 * <p>在线实现：中位数与绝对偏差中位数均用 P² 在线分位数估计（O(1) 内存）。
 * <b>冷启动门禁（评审要求）</b>：样本 <minSamples 时不判定——与 CUSUM 同门槛，
 * 避免上线初期用不可靠的分位数误判真突变（直接冲击 NFR-A2 误报率 ≤10%）。</p>
 */
public class MaddOutlierFilter {

    /** 异常判定系数（默认 3，即 3×1.4826×MAD） */
    private final double thresholdFactor;

    /** 冷启动最小样本数 */
    private final int minSamples;

    private final P2Median valueMedian = new P2Median();

    /** |x − 中位数| 的中位数（MAD 估计；使用当前中位数的在线近似） */
    private final P2Median deviationMedian = new P2Median();

    private long count;

    public MaddOutlierFilter(int minSamples) {
        this(3.0D, minSamples);
    }

    public MaddOutlierFilter(double thresholdFactor, int minSamples) {
        this.thresholdFactor = thresholdFactor;
        this.minSamples = minSamples;
    }

    /** 追加样本（正常样本喂给滤波器维持分位数） */
    public void update(double x) {
        valueMedian.add(x);
        deviationMedian.add(Math.abs(x - valueMedian.median()));
        count++;
    }

    /** 判定是否为异常点（冷启动期一律返回 false） */
    public boolean isOutlier(double x) {
        if (count < minSamples) {
            return false; // 冷启动门禁：分位数估计不可靠，不判定
        }
        double median = valueMedian.median();
        double mad = deviationMedian.median();
        if (Double.isNaN(median) || mad <= 0D) {
            return false;
        }
        return Math.abs(x - median) > thresholdFactor * 1.4826D * mad;
    }

    public double currentMedian() {
        return valueMedian.median();
    }

    public double currentMad() {
        return deviationMedian.median();
    }

    public long sampleCount() {
        return count;
    }
}
