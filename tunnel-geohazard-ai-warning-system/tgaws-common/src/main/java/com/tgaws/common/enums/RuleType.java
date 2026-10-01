package com.tgaws.common.enums;

import java.util.Arrays;

/**
 * 规则类型（《5.算法设计说明书》5.4：阈值/速率/突变/组合）。
 */
public enum RuleType {

    THRESHOLD_UPPER(1, "阈值上限"),
    THRESHOLD_LOWER(2, "阈值下限"),
    RATE(3, "速率"),
    MUTATION(4, "突变"),
    COMBINATION(5, "组合");

    private final int value;
    private final String label;

    RuleType(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public static RuleType of(int value) {
        return Arrays.stream(values())
                .filter(e -> e.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知规则类型: " + value));
    }
}
