package com.tgaws.business.mon.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 监测点位实体（mon_point，台账全字段）。
 */
public class PointEntity {

    private Long id;
    private String pointCode;
    private String pointName;
    private Long tunnelId;
    private Long sectionId;
    private Integer hazardType;
    private Integer itemType;
    private String unit;
    private BigDecimal rangeMin;
    private BigDecimal rangeMax;
    private Integer scale;
    private String installPosition;
    private String gatewayCode;
    private Integer protocol;
    private Integer collectFreqSec;
    private Integer collectEnabled;
    private Integer enabled;
    private Integer onlineStatus;
    private LocalDateTime lastDataTime;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

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

    public Long getSectionId() {
        return sectionId;
    }

    public void setSectionId(Long sectionId) {
        this.sectionId = sectionId;
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

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getRangeMin() {
        return rangeMin;
    }

    public void setRangeMin(BigDecimal rangeMin) {
        this.rangeMin = rangeMin;
    }

    public BigDecimal getRangeMax() {
        return rangeMax;
    }

    public void setRangeMax(BigDecimal rangeMax) {
        this.rangeMax = rangeMax;
    }

    public Integer getScale() {
        return scale;
    }

    public void setScale(Integer scale) {
        this.scale = scale;
    }

    public String getInstallPosition() {
        return installPosition;
    }

    public void setInstallPosition(String installPosition) {
        this.installPosition = installPosition;
    }

    public String getGatewayCode() {
        return gatewayCode;
    }

    public void setGatewayCode(String gatewayCode) {
        this.gatewayCode = gatewayCode;
    }

    public Integer getProtocol() {
        return protocol;
    }

    public void setProtocol(Integer protocol) {
        this.protocol = protocol;
    }

    public Integer getCollectFreqSec() {
        return collectFreqSec;
    }

    public void setCollectFreqSec(Integer collectFreqSec) {
        this.collectFreqSec = collectFreqSec;
    }

    public Integer getCollectEnabled() {
        return collectEnabled;
    }

    public void setCollectEnabled(Integer collectEnabled) {
        this.collectEnabled = collectEnabled;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public Integer getOnlineStatus() {
        return onlineStatus;
    }

    public void setOnlineStatus(Integer onlineStatus) {
        this.onlineStatus = onlineStatus;
    }

    public LocalDateTime getLastDataTime() {
        return lastDataTime;
    }

    public void setLastDataTime(LocalDateTime lastDataTime) {
        this.lastDataTime = lastDataTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
