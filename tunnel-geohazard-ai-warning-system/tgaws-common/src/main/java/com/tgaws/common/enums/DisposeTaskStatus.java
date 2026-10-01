package com.tgaws.common.enums;

import java.util.Arrays;

/**
 * 处置任务状态（《3》3.7.2：1→2→3；1/2→4 超时）。
 */
public enum DisposeTaskStatus {

    PENDING(1, "待处置"),
    IN_PROGRESS(2, "处置中"),
    FINISHED(3, "已完成"),
    OVERDUE(4, "超时未完成");

    private final int value;
    private final String label;

    DisposeTaskStatus(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public static DisposeTaskStatus of(int value) {
        return Arrays.stream(values())
                .filter(e -> e.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知处置任务状态: " + value));
    }
}
