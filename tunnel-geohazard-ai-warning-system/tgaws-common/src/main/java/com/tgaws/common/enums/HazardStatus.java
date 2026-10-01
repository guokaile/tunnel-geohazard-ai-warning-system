package com.tgaws.common.enums;

import java.util.Arrays;

/**
 * 隐患状态（字典 hazard_status）。
 */
public enum HazardStatus {

    PENDING(1, "待处置"),
    IN_PROGRESS(2, "处置中"),
    CLOSED(3, "已闭环");

    private final int value;
    private final String label;

    HazardStatus(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public static HazardStatus of(int value) {
        return Arrays.stream(values())
                .filter(e -> e.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知隐患状态: " + value));
    }
}
