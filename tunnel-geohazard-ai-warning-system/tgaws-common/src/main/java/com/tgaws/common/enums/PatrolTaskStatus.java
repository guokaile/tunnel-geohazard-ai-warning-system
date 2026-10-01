package com.tgaws.common.enums;

import java.util.Arrays;

/**
 * 巡检任务状态（《3》3.7.2：1→2→3；1→4 超时）。
 */
public enum PatrolTaskStatus {

    PENDING(1, "待巡检"),
    IN_PROGRESS(2, "进行中"),
    FINISHED(3, "已完成"),
    OVERDUE(4, "逾期");

    private final int value;
    private final String label;

    PatrolTaskStatus(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public static PatrolTaskStatus of(int value) {
        return Arrays.stream(values())
                .filter(e -> e.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知巡检任务状态: " + value));
    }
}
