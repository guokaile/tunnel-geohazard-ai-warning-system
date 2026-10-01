package com.tgaws.common.enums;

import java.util.Arrays;

/**
 * 模型类型（字典 model_type；ML 为二期预留）。
 */
public enum ModelType {

    RULE(1, "规则"),
    STATISTICAL(2, "统计"),
    ML(3, "ML（二期）");

    private final int value;
    private final String label;

    ModelType(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public static ModelType of(int value) {
        return Arrays.stream(values())
                .filter(e -> e.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知模型类型: " + value));
    }
}
