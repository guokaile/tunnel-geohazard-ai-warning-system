package com.tgaws.business.patrol.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 巡检隐患实体（patrol_hazard 行，FR-504 登记→处置→闭环全生命周期）。
 */
public class PatrolHazardEntity {

    private Long id;
    private String hazardNo;
    private Long tunnelId;
    private Long sectionId;
    private Integer source;
    private String title;
    private String description;
    private Integer hazardLevel;
    private String images;
    private Integer status;
    private Long handlerId;
    private Long discoverUserId;
    private LocalDateTime discoverTime;
    private LocalDateTime closeTime;
    private String closeRemark;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private Long taskId;
    private Long recordId;
    private Integer hazardType;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getHazardNo() {
        return hazardNo;
    }

    public void setHazardNo(String hazardNo) {
        this.hazardNo = hazardNo;
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

    public Integer getSource() {
        return source;
    }

    public void setSource(Integer source) {
        this.source = source;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getHazardLevel() {
        return hazardLevel;
    }

    public void setHazardLevel(Integer hazardLevel) {
        this.hazardLevel = hazardLevel;
    }

    public String getImages() {
        return images;
    }

    public void setImages(String images) {
        this.images = images;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getHandlerId() {
        return handlerId;
    }

    public void setHandlerId(Long handlerId) {
        this.handlerId = handlerId;
    }

    public Long getDiscoverUserId() {
        return discoverUserId;
    }

    public void setDiscoverUserId(Long discoverUserId) {
        this.discoverUserId = discoverUserId;
    }

    public LocalDateTime getDiscoverTime() {
        return discoverTime;
    }

    public void setDiscoverTime(LocalDateTime discoverTime) {
        this.discoverTime = discoverTime;
    }

    public LocalDateTime getCloseTime() {
        return closeTime;
    }

    public void setCloseTime(LocalDateTime closeTime) {
        this.closeTime = closeTime;
    }

    public String getCloseRemark() {
        return closeRemark;
    }

    public void setCloseRemark(String closeRemark) {
        this.closeRemark = closeRemark;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Integer getHazardType() {
        return hazardType;
    }

    public void setHazardType(Integer hazardType) {
        this.hazardType = hazardType;
    }
}
