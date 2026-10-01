package com.tgaws.compute.detect;

import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * T4 验证（Commons Math + JDK 25）：最小二乘回归拟合 y=2x+1。
 * 证据：T4 与 T-501（Aviator 组合规则 7 用例）共同构成验收。
 */
class CommonsMathSmokeTest {

    @Test
    void simpleRegressionFitsKnownLine() {
        SimpleRegression regression = new SimpleRegression();
        for (int i = 0; i <= 100; i++) {
            regression.addData(i, 2.0 * i + 1.0);
        }
        assertEquals(2.0D, regression.getSlope(), 1e-9, "斜率应还原为 2");
        assertEquals(1.0D, regression.getIntercept(), 1e-9, "截距应还原为 1");
    }
}
