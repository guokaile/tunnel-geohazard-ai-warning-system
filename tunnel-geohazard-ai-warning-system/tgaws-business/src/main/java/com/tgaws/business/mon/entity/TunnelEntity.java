package com.tgaws.business.mon.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 隧道工程实体（prj_tunnel）。
 */
public class TunnelEntity {

    private Long id;
    private String tunnelCode;
    private String tunnelName;
    private Integer tunnelType;
    private Integer stage;
    private BigDecimal lengthM;
    private String geoDesc;
    private Integer status;
    private LocalDate startDate;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTunnelCode() {
        return tunnelCode;
    }

    public void setTunnelCode(String tunnelCode) {
        this.tunnelCode = tunnelCode;
    }

    public String getTunnelName() {
        return tunnelName;
    }

    public void setTunnelName(String tunnelName) {
        this.tunnelName = tunnelName;
    }

    public Integer getTunnelType() {
        return tunnelType;
    }

    public void setTunnelType(Integer tunnelType) {
        this.tunnelType = tunnelType;
    }

    public Integer getStage() {
        return stage;
    }

    public void setStage(Integer stage) {
        this.stage = stage;
    }

    public BigDecimal getLengthM() {
        return lengthM;
    }

    public void setLengthM(BigDecimal lengthM) {
        this.lengthM = lengthM;
    }

    public String getGeoDesc() {
        return geoDesc;
    }

    public void setGeoDesc(String geoDesc) {
        this.geoDesc = geoDesc;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
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
