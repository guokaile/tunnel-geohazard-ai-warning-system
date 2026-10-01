package com.tgaws.common.enums;

import java.util.Arrays;

/**
 * 模型上线门禁阶段（《5.算法设计说明书》5.8.2 影子/灰度/生效，配置 model.gate.stage）。
 */
public enum GateStage {

    /** 影子期：只计算落库，只标记不通知（≥30 天） */
    SHADOW("shadow", "影子期"),
    /** 灰度期：蓝/黄正常通知，橙/红人工确认（≥14 天） */
    GRAY("gray", "灰度期"),
    /** 生效期：全级别自动通知 */
    LIVE("live", "生效期");

    private final String value;
    private final String label;

    GateStage(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public static GateStage of(String value) {
        return Arrays.stream(values())
                .filter(e -> e.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知门禁阶段: " + value));
    }
}
