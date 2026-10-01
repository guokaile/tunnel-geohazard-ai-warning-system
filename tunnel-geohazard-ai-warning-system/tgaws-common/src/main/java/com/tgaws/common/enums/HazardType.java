package com.tgaws.common.enums;

import java.util.Arrays;

/**
 * 监测对象（字典 hazard_type，对应《3》3.7.1）。
 */
public enum HazardType {

    COLLAPSE(1, "坍塌"),
    WATER_INRUSH(2, "涌水/突水"),
    GAS(3, "瓦斯及有害气体"),
    MUD_INRUSH(4, "突泥"),
    SETTLEMENT(5, "地表沉降/拱顶下沉"),
    CONVERGENCE(6, "围岩收敛位移");

    private final int value;
    private final String label;

    HazardType(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    /** 按字典值解析，未命中抛异常 */
    public static HazardType of(int value) {
        return Arrays.stream(values())
                .filter(e -> e.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知监测对象编码: " + value));
    }
}
