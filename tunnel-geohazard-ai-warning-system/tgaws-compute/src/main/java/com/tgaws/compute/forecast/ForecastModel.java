package com.tgaws.compute.forecast;

/**
 * 预测模型类型（《5.算法设计说明书》5.5.1 模型选择策略）。
 */
public enum ForecastModel {

    /** 指数加权滑动平均（短时高波动序列：平滑外推） */
    EWMA("ewma"),

    /** 最小二乘线性回归 + 置信区间（≥7d 且趋势显著：中期趋势外推，提前预警核心） */
    LINEAR("linear"),

    /** Holt 双参数指数平滑（长期缓变监测，沉降类） */
    HOLT("holt");

    private final String code;

    ForecastModel(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
