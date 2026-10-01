package com.tgaws.compute.detect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Welford 在线统计单测（1..5：均值 3、样本方差 2.5）。
 */
class WelfordStatsTest {

    @Test
    void knownSequence() {
        WelfordStats stats = new WelfordStats();
        for (int i = 1; i <= 5; i++) {
            stats.add(i);
        }
        assertEquals(5, stats.count());
        assertEquals(3.0D, stats.mean(), 1e-12);
        assertEquals(2.5D, stats.variance(), 1e-12, "样本方差（n-1）");
        assertEquals(Math.sqrt(2.5D), stats.stddev(), 1e-12);
    }

    @Test
    void resetReinitializes() {
        WelfordStats stats = new WelfordStats();
        stats.add(10);
        stats.add(20);
        stats.reset();
        assertEquals(0, stats.count());
        assertEquals(0.0D, stats.mean(), 1e-12);
    }
}
