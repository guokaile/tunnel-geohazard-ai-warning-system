package com.tgaws.common.enums;

import java.util.Arrays;

/**
 * 通知投递状态（Outbox 模式，warn_notify_log.deliver_state）。
 */
public enum DeliverState {

    PENDING(0, "待投递"),
    DELIVERED(1, "已投递"),
    RETRYING(2, "失败待补偿"),
    ABANDONED(3, "已放弃");

    private final int value;
    private final String label;

    DeliverState(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public static DeliverState of(int value) {
        return Arrays.stream(values())
                .filter(e -> e.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知投递状态: " + value));
    }
}
