package com.tgaws.compute.detect;

import java.util.Arrays;

/**
 * P² 在线分位数估计算法（O(1) 内存的中位数估计，5 标记点）——
 * 《5.算法设计说明书》附录D：MAD 分位数用 P² 在线估计，不保留原始序列。
 *
 * <p>参考 Jain & Chlamtac (1985) "The P² Algorithm for Dynamic Calculation of
 * Quantiles and Histograms Without Storing Observations"。</p>
 */
public class P2Median {

    /** 5 个标记点高度 */
    private final double[] q = new double[5];

    /** 5 个标记点位置 */
    private final int[] n = new int[5];

    private long count;

    public void add(double x) {
        count++;
        if (count <= 5) {
            q[(int) count - 1] = x;
            if (count == 5) {
                Arrays.sort(q);
                for (int i = 0; i < 5; i++) {
                    n[i] = i + 1;
                }
            }
            return;
        }
        int k;
        if (x < q[0]) {
            q[0] = x;
            k = 0;
        } else if (x >= q[4]) {
            q[4] = x;
            k = 3;
        } else if (x < q[1]) {
            k = 0;
        } else if (x < q[2]) {
            k = 1;
        } else if (x < q[3]) {
            k = 2;
        } else {
            k = 3;
        }
        for (int i = k + 1; i < 5; i++) {
            n[i]++;
        }
        for (int i = 0; i < 5; i++) {
            double desired = 1 + i * (count - 1) / 4.0D;
            double di = desired - n[i];
            if ((di >= 1 && i < 4 && n[i + 1] - n[i] > 1)
                    || (di <= -1 && i > 0 && n[i] - n[i - 1] > 1)) {
                int d = di > 0 ? 1 : -1;
                double qp = parabolic(i, d);
                if (q[i - 1] < qp && qp < q[i + 1]) {
                    q[i] = qp;
                } else {
                    q[i] = linear(i, d);
                }
                n[i] += d;
            }
        }
    }

    /** 当前中位数估计（无样本返回 NaN） */
    public double median() {
        return count == 0 ? Double.NaN : q[2];
    }

    private double parabolic(int i, int d) {
        return q[i] + d / (double) (n[i + 1] - n[i - 1])
                * ((n[i] - n[i - 1] + d) * (q[i + 1] - q[i]) / (double) (n[i + 1] - n[i])
                + (n[i + 1] - n[i] - d) * (q[i] - q[i - 1]) / (double) (n[i] - n[i - 1]));
    }

    private double linear(int i, int d) {
        return q[i] + d * (q[i + d] - q[i]) / (double) (n[i + d] - n[i]);
    }
}
