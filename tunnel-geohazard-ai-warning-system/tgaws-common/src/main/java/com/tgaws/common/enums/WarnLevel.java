package com.tgaws.common.enums;

import java.util.Arrays;

/**
 * 预警级别（蓝黄橙红，对应需求 1.6.3 响应动作映射表）。
 */
public enum WarnLevel {

    BLUE(1, "蓝色（关注）"),
    YELLOW(2, "黄色（警戒）"),
    ORANGE(3, "橙色（处置）"),
    RED(4, "红色（紧急）");

    private final int value;
    private final String label;

    WarnLevel(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public static WarnLevel of(int value) {
        return Arrays.stream(values())
                .filter(e -> e.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知预警级别: " + value));
    }
}
