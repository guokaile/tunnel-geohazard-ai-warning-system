package com.tgaws.common.enums;

import java.util.Arrays;

/**
 * 测项（字典 item_type，3 位分组编码：百位=监测对象，对应《3》3.7.1）。
 */
public enum ItemType {

    SURROUNDING_DISPLACEMENT(101, "围岩位移"),
    ANCHOR_AXIAL_FORCE(102, "锚杆轴力"),
    STEEL_ARCH_STRESS(103, "钢架应力"),
    WATER_INFLOW(201, "涌水量"),
    WATER_PRESSURE(202, "水压"),
    GROUNDWATER_LEVEL(203, "地下水位"),
    CH4_CONCENTRATION(301, "CH4浓度"),
    CO_CONCENTRATION(302, "CO浓度"),
    H2S_CONCENTRATION(303, "H2S浓度"),
    WIND_SPEED(304, "风速"),
    MUD_FLOW(401, "泥水流量"),
    PORE_WATER_PRESSURE(402, "孔隙水压"),
    SETTLEMENT_VALUE(501, "沉降量"),
    SETTLEMENT_RATE(502, "下沉速率"),
    CLEARANCE_CONVERGENCE(601, "净空收敛"),
    PERIPHERAL_DISPLACEMENT(602, "周边位移");

    private final int value;
    private final String label;

    ItemType(int value, String label) {
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
    public static ItemType of(int value) {
        return Arrays.stream(values())
                .filter(e -> e.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知测项编码: " + value));
    }
}
