package com.tgaws.common.enums;

import java.util.Arrays;

/**
 * 预警事件状态（《3》3.7.2 状态机：1→2→3→4→5；1→6；4→3）。
 */
public enum WarnStatus {

    PENDING_CONFIRM(1, "待确认"),
    CONFIRMED(2, "已确认"),
    DISPOSING(3, "处置中"),
    PENDING_REVIEW(4, "待复核"),
    CLOSED(5, "已消警"),
    FALSE_ALARM(6, "误报关闭");

    private final int value;
    private final String label;

    WarnStatus(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public static WarnStatus of(int value) {
        return Arrays.stream(values())
                .filter(e -> e.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知预警状态: " + value));
    }
}
