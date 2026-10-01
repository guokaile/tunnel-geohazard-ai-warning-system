package com.tgaws.compute.detect;

/**
 * Welford 在线均值/方差（O(1) 内存增量统计量，不保留原始序列——《5》附录D 内存预算依据）。
 */
public class WelfordStats {

    private long n;
    private double mean;
    private double m2;

    /** 追加样本 */
    public void add(double x) {
        n++;
        double delta = x - mean;
        mean += delta / n;
        m2 += delta * (x - mean);
    }

    public long count() {
        return n;
    }

    public double mean() {
        return mean;
    }

    /** 样本方差（n-1 分母；n<2 返回 0） */
    public double variance() {
        return n < 2 ? 0D : m2 / (n - 1);
    }

    /** 样本标准差 */
    public double stddev() {
        return Math.sqrt(variance());
    }

    /** 重置（消警后受污染窗口重建基线） */
    public void reset() {
        n = 0;
        mean = 0D;
        m2 = 0D;
    }
}
