package com.tgaws.common.enums;

import java.util.Arrays;

/**
 * 数据质量位（FR-102 校验清洗：异常数据不静默丢弃）。
 */
public enum QualityFlag {

    NORMAL(0, "正常"),
    OUT_OF_RANGE(1, "超范围"),
    JUMP(2, "跳变"),
    MISSING(3, "缺失");

    private final int value;
    private final String label;

    QualityFlag(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public static QualityFlag of(int value) {
        return Arrays.stream(values())
                .filter(e -> e.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知质量位: " + value));
    }

    /** 研判降权系数（《5.算法设计说明书》5.3：正常 1.0/超范围 0.8/跳变 0.5/缺失不计） */
    public double judgeWeight() {
        return switch (this) {
            case NORMAL -> 1.0D;
            case OUT_OF_RANGE -> 0.8D;
            case JUMP -> 0.5D;
            case MISSING -> 0D;
        };
    }
}
