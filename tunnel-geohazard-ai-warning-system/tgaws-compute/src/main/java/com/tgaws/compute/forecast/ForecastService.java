package com.tgaws.compute.forecast;

import com.tgaws.common.rule.PointSample;
import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.stat.regression.SimpleRegression;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 趋势预测服务（《5.算法设计说明书》5.5）：
 *
 * <p>模型选择（5.5.1）：样本 <24h 不启用（冷启动防模型噪声）→ 波动大（σ/μ>0.3）用 EWMA →
 * 样本 ≥7d 且趋势显著（|斜率|/标准误 >2）用线性回归+置信区间 → 其余用 Holt 双参数平滑。</p>
 *
 * <p>输出：未来 H 分钟（默认 30，步长 5）预测值与置信区间；供趋势预警（低一级提示）
 * 与综合研判置信度输入，**不单独直接定级**。</p>
 */
public class ForecastService {

    /** 预测时域（分钟），默认 30 */
    public static final int DEFAULT_HORIZON_MINUTES = 30;

    /** 预测步长（分钟） */
    public static final int STEP_MINUTES = 5;

    /** 波动判定阈值（σ/μ） */
    public static final double VOLATILITY_RATIO = 0.3D;

    /** EWMA 平滑系数 */
    private static final double EWMA_ALPHA = 0.3D;

    /** Holt 水平/趋势系数 */
    private static final double HOLT_ALPHA = 0.4D;
    private static final double HOLT_BETA = 0.2D;

    /** 趋势显著性 t 值（|斜率|/标准误 阈值） */
    private static final double TREND_T = 2.0D;

    private final int minTrainSamples;
    private final int minTrendSamples;

    public ForecastService(int minTrainSamples, int minTrendSamples) {
        this.minTrainSamples = minTrainSamples;
        this.minTrendSamples = minTrendSamples;
    }

    /**
     * 趋势预测。
     *
     * @param pointId 点位 id
     * @param nowMs   当前时间（Unix 毫秒）
     * @param samples 分钟级窗口样本（时间升序）
     * @return 冷启动/样本不足返回 empty
     */
    public Optional<ForecastResult> forecast(long pointId, long nowMs, List<PointSample> samples) {
        if (samples == null || samples.size() < minTrainSamples) {
            return Optional.empty(); // 冷启动：最小样本量不足，不启用预测
        }
        double[] values = samples.stream().mapToDouble(s -> s.value().doubleValue()).toArray();
        double mean = mean(values);
        double sd = stddev(values, mean);
        ForecastModel model;
        // 选择顺序（5.5.1）：≥7d 先判趋势显著性——坡道序列的 σ/μ 会被趋势本身放大，
        // 波动检查必须后置，否则趋势序列被误分为"高波动"→EWMA
        if (samples.size() >= minTrendSamples && trendSignificant(values)) {
            model = ForecastModel.LINEAR;
        } else if (sd > VOLATILITY_RATIO * Math.max(Math.abs(mean), 1e-9)) {
            model = ForecastModel.EWMA;
        } else {
            model = ForecastModel.HOLT;
        }
        List<ForecastPoint> points = switch (model) {
            case EWMA -> forecastEwma(values, nowMs, sd);
            case LINEAR -> forecastLinear(values, nowMs);
            case HOLT -> forecastHolt(values, nowMs, sd);
        };
        return Optional.of(new ForecastResult(pointId, nowMs, model, points));
    }

    // ---------- 各模型 ----------

    /** EWMA：平滑值外推（平坦），置信带 ±2σ */
    private List<ForecastPoint> forecastEwma(double[] values, long nowMs, double sd) {
        double e = values[0];
        for (int i = 1; i < values.length; i++) {
            e = EWMA_ALPHA * values[i] + (1 - EWMA_ALPHA) * e;
        }
        final double ewmaValue = e;
        return buildPoints(nowMs, i -> ewmaValue, i -> ewmaValue - 2 * sd, i -> ewmaValue + 2 * sd);
    }

    /** 线性回归 + 95% 预测区间（t_{0.975,n-2}） */
    private List<ForecastPoint> forecastLinear(double[] values, long nowMs) {
        SimpleRegression regression = new SimpleRegression();
        for (int i = 0; i < values.length; i++) {
            regression.addData(i, values[i]);
        }
        int n = values.length;
        double slope = regression.getSlope();
        double intercept = regression.getIntercept();
        double se = Math.sqrt(regression.getMeanSquareError());
        double t = new TDistribution(n - 2).inverseCumulativeProbability(0.975D);
        double meanIdx = (n - 1) / 2.0D;
        double sxx = 0;
        for (int i = 0; i < n; i++) {
            sxx += (i - meanIdx) * (i - meanIdx);
        }
        final double sxxFinal = sxx;
        return buildPoints(nowMs,
                h -> intercept + slope * (n - 1 + h),
                h -> {
                    double y = intercept + slope * (n - 1 + h);
                    double sePred = se * Math.sqrt(1 + 1.0D / n
                            + (n - 1 + h - meanIdx) * (n - 1 + h - meanIdx) / sxxFinal);
                    return y - t * sePred;
                },
                h -> {
                    double y = intercept + slope * (n - 1 + h);
                    double sePred = se * Math.sqrt(1 + 1.0D / n
                            + (n - 1 + h - meanIdx) * (n - 1 + h - meanIdx) / sxxFinal);
                    return y + t * sePred;
                });
    }

    /** Holt 双参数平滑：ŷ = level + h×trend，置信带 ±2σ */
    private List<ForecastPoint> forecastHolt(double[] values, long nowMs, double sd) {
        double level = values[0];
        double trend = values[1] - values[0];
        for (int i = 1; i < values.length; i++) {
            double prevLevel = level;
            level = HOLT_ALPHA * values[i] + (1 - HOLT_ALPHA) * (level + trend);
            trend = HOLT_BETA * (level - prevLevel) + (1 - HOLT_BETA) * trend;
        }
        final double levelFinal = level;
        final double trendFinal = trend;
        return buildPoints(nowMs,
                h -> levelFinal + h * trendFinal,
                h -> levelFinal + h * trendFinal - 2 * sd,
                h -> levelFinal + h * trendFinal + 2 * sd);
    }

    // ---------- 内部 ----------

    private boolean trendSignificant(double[] values) {
        SimpleRegression regression = new SimpleRegression();
        for (int i = 0; i < values.length; i++) {
            regression.addData(i, values[i]);
        }
        if (regression.getN() < 3 || regression.getSlopeStdErr() <= 0) {
            return false;
        }
        return Math.abs(regression.getSlope() / regression.getSlopeStdErr()) > TREND_T;
    }

    private List<ForecastPoint> buildPoints(long nowMs,
                                            java.util.function.IntToDoubleFunction value,
                                            java.util.function.IntToDoubleFunction lower,
                                            java.util.function.IntToDoubleFunction upper) {
        List<ForecastPoint> points = new ArrayList<>();
        for (int h = STEP_MINUTES; h <= DEFAULT_HORIZON_MINUTES; h += STEP_MINUTES) {
            points.add(new ForecastPoint(
                    nowMs + h * 60_000L,
                    bd(value.applyAsDouble(h)),
                    bd(lower.applyAsDouble(h)),
                    bd(upper.applyAsDouble(h))));
        }
        return points;
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }

    private static double mean(double[] values) {
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    private static double stddev(double[] values, double mean) {
        double sum = 0;
        for (double v : values) {
            sum += (v - mean) * (v - mean);
        }
        return Math.sqrt(sum / (values.length - 1));
    }

    /**
     * 预测结果。
     *
     * @param pointId      点位 id
     * @param forecastAtMs 批算时刻
     * @param model        选用模型
     * @param points       预测点列（步长 5min）
     */
    public record ForecastResult(long pointId, long forecastAtMs, ForecastModel model,
                                 List<ForecastPoint> points) {
    }

    /**
     * 预测点。
     *
     * @param targetTimeMs 目标时刻
     * @param value        预测值
     * @param lower        置信区间下界
     * @param upper        置信区间上界
     */
    public record ForecastPoint(long targetTimeMs, BigDecimal value, BigDecimal lower, BigDecimal upper) {
    }
}
