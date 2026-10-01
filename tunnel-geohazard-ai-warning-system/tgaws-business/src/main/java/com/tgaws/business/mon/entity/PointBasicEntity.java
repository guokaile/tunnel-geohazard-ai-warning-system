package com.tgaws.business.mon.entity;

/**
 * 点位基础信息（判定编排用：隧道/监测对象/测项）。
 */
public class PointBasicEntity {

    private Long id;
    private String pointCode;
    private String pointName;
    private Long tunnelId;
    private Integer hazardType;
    private Integer itemType;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPointCode() {
        return pointCode;
    }

    public void setPointCode(String pointCode) {
        this.pointCode = pointCode;
    }

    public String getPointName() {
        return pointName;
    }

    public void setPointName(String pointName) {
        this.pointName = pointName;
    }

    public Long getTunnelId() {
        return tunnelId;
    }

    public void setTunnelId(Long tunnelId) {
        this.tunnelId = tunnelId;
    }

    public Integer getHazardType() {
        return hazardType;
    }

    public void setHazardType(Integer hazardType) {
        this.hazardType = hazardType;
    }

    public Integer getItemType() {
        return itemType;
    }

    public void setItemType(Integer itemType) {
        this.itemType = itemType;
    }
}
