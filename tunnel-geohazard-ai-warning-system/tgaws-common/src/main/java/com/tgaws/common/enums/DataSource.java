package com.tgaws.common.enums;

import java.util.Arrays;

/**
 * 数据来源（字典 data_source）。
 */
public enum DataSource {

    TCP(1, "TCP"),
    MQTT(2, "MQTT"),
    MANUAL(3, "人工录入"),
    FILE_IMPORT(4, "文件导入"),
    THIRD_PARTY(5, "第三方REST");

    private final int value;
    private final String label;

    DataSource(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public static DataSource of(int value) {
        return Arrays.stream(values())
                .filter(e -> e.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知数据来源: " + value));
    }
}
