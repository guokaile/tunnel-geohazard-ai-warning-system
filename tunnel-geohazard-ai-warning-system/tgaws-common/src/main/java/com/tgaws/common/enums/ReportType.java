package com.tgaws.common.enums;

import java.util.Arrays;

/**
 * 报告类型（字典 report_type）。
 */
public enum ReportType {

    DAILY(1, "日报"),
    WEEKLY(2, "周报"),
    MONTHLY(3, "月报");

    private final int value;
    private final String label;

    ReportType(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public static ReportType of(int value) {
        return Arrays.stream(values())
                .filter(e -> e.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知报告类型: " + value));
    }
}
