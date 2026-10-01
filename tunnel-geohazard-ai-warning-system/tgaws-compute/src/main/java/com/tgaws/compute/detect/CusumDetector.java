package com.tgaws.compute.detect;

import com.tgaws.common.rule.PointSample;

import java.util.Optional;

/**
 * CUSUM 累积和突变检测（《5.算法设计说明书》5.4.3，公式 F6）。
 *
 * <p>增量实现（O(1) 内存）：S⁺/S⁻ 两个标量 + Welford 基线统计。</p>
 * <ul>
 *   <li><b>参数单位</b>：k=允许漂移量（参考值 K）、h=判决阈值，单位均为窗口标准差 σ 的倍数；</li>
 *   <li><b>冷启动</b>：样本 <minSamples 不启用（与预测/ MAD 同门槛，防上线初期误判真突变）；</li>
 *   <li><b>冻结基线 + 显式刷新</b>：暖机完成后 μ₀/σ 冻结（防"永久增长型"窗口缓慢漂移
 *       造成平稳序列累计偏差误报）；refreshBaseline() 用新暖机窗口的统计量替换
 *       （对应 μ₀ 每 6h 自适应更新，由 T-505 定时驱动）；</li>
 *   <li><b>基线保护</b>：shield() 屏蔽期不喂暖机窗口（预警期防污染）；resetBaseline()
 *       消警后重建（丢弃受污染窗口，防异常残留抬高基线导致同类异常漏报）。</li>
 * </ul>
 */
public class CusumDetector {

    /** 允许漂移量（参考值 K，σ 倍数），默认 0.5 */
    private final double kSigma;

    /** 判决阈值（σ 倍数），默认 5 */
    private final double hSigma;

    /** 冷启动最小样本数（由调用方按点位采集频率换算，如 24h 样本量） */
    private final int minSamples;

    /** 检测方向（业务突变事件多为突增：涌水/突泥/瓦斯浓度；DOWN 用于水位骤降类镜像） */
    private final Direction direction;

    /** 当前暖机窗口（累计满 minSamples 后冻结为 μ₀/σ） */
    private WelfordStats warmup = new WelfordStats();

    /** 下一暖机窗口（显式刷新用） */
    private WelfordStats nextWarmup = new WelfordStats();

    /** 冻结基线 */
    private double mu0;
    private double sigma0;
    private boolean enabled;

    private double sPlus;
    private double sMinus;
    private boolean shielded;
    private long totalCount;

    public CusumDetector(int minSamples) {
        // 默认 h=8σ（2026-09-27 参数决策：h=5σ 实测误报率 0.08%/样本，5000 点位 ≈5760 次/天；
        // 升 8σ 后 ≈1.9 次/天；分测项档位见《5》5.4.3，由 T-505 按 sys_config 注入）
        this(0.5D, 8.0D, minSamples, Direction.UP);
    }

    public CusumDetector(double kSigma, double hSigma, int minSamples) {
        this(kSigma, hSigma, minSamples, Direction.UP);
    }

    public CusumDetector(double kSigma, double hSigma, int minSamples, Direction direction) {
        this.kSigma = kSigma;
        this.hSigma = hSigma;
        this.minSamples = minSamples;
        this.direction = direction;
    }

    /**
     * 追加样本并判定。
     *
     * @return 命中时返回检测结果；未启用/未命中返回 empty
     */
    public Optional<CusumResult> update(PointSample sample) {
        double x = sample.value().doubleValue();
        totalCount++;
        if (!shielded) {
            if (!enabled) {
                warmup.add(x);
                if (warmup.count() >= minSamples) {
                    freeze();
                }
            } else {
                nextWarmup.add(x);
            }
        }
        if (!enabled || sigma0 <= 0D) {
            return Optional.empty(); // 冷启动：仅积累基线，不判定
        }
        double z = x - mu0;
        boolean upHit = false;
        boolean downHit = false;
        if (direction == Direction.UP || direction == Direction.BOTH) {
            sPlus = Math.max(0D, sPlus + z - kSigma * sigma0);
            upHit = sPlus > hSigma * sigma0;
        }
        if (direction == Direction.DOWN || direction == Direction.BOTH) {
            sMinus = Math.min(0D, sMinus + z + kSigma * sigma0);
            downHit = sMinus < -hSigma * sigma0;
        }
        if (upHit || downHit) {
            double hit = upHit ? sPlus : sMinus;
            sPlus = 0D;
            sMinus = 0D;
            return Optional.of(new CusumResult(true, hit, mu0, sigma0));
        }
        return Optional.of(new CusumResult(false, Math.max(sPlus, -sMinus), mu0, sigma0));
    }

    /** 显式刷新基线（新暖机窗口满 minSamples 后生效；对应 μ₀ 自适应更新周期） */
    public void refreshBaseline() {
        if (nextWarmup.count() >= minSamples) {
            warmup = nextWarmup;
            nextWarmup = new WelfordStats();
            freeze();
        }
    }

    /** 屏蔽基线更新（预警期调用：窗口内存在预警事件时不更新 μ₀） */
    public void shield() {
        this.shielded = true;
    }

    /** 解除屏蔽 */
    public void unshield() {
        this.shielded = false;
    }

    /** 消警后重建基线（丢弃受污染窗口） */
    public void resetBaseline() {
        warmup = new WelfordStats();
        nextWarmup = new WelfordStats();
        enabled = false;
        mu0 = 0D;
        sigma0 = 0D;
        sPlus = 0D;
        sMinus = 0D;
        totalCount = 0;
    }

    private void freeze() {
        mu0 = warmup.mean();
        sigma0 = warmup.stddev();
        enabled = true;
    }

    public double baselineMean() {
        return enabled ? mu0 : warmup.mean();
    }

    public double baselineStddev() {
        return enabled ? sigma0 : warmup.stddev();
    }

    public long sampleCount() {
        return totalCount;
    }

    /**
     * 检测结果。
     *
     * @param hit      是否命中突变
     * @param cumsum   累积和（S⁺ 或 S⁻ 的绝对值）
     * @param baseline 基线均值 μ₀
     * @param sigma    窗口标准差 σ
     */
    public record CusumResult(boolean hit, double cumsum, double baseline, double sigma) {
    }

    /** 检测方向 */
    public enum Direction {
        /** 仅正向突增（涌水/突泥/瓦斯浓度，默认） */
        UP,
        /** 仅负向骤降（水位骤降类） */
        DOWN,
        /** 双向 */
        BOTH
    }
}
