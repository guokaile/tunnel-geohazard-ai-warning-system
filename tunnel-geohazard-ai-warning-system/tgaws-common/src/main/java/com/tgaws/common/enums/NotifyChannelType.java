package com.tgaws.common.enums;

import java.util.Arrays;

/**
 * 通知渠道（字典 notify_channel；4=电话语音为二期）。
 */
public enum NotifyChannelType {

    LIGHT_ALARM(1, "声光报警"),
    SMS(2, "短信"),
    INNER(3, "站内消息"),
    PHONE(4, "电话语音（二期）");

    private final int value;
    private final String label;

    NotifyChannelType(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public static NotifyChannelType of(int value) {
        return Arrays.stream(values())
                .filter(e -> e.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知通知渠道: " + value));
    }
}
