package com.tgaws.business.warn.entity;

import java.time.LocalDateTime;

/**
 * 预警事件实体（warn_event 行，T-505 最小字段集）。
 */
public class WarnEventEntity {

    private Long id;
    private String eventNo;
    private Long tunnelId;
    private Long pointId;
    private Integer hazardType;
    private Integer itemType;
    private Integer warnLevel;
    private String warnTitle;
    private String warnContent;
    private Integer warnStatus;
    private Integer gateStage;
    private Long hazardEventId;
    private String triggerValue;
    private LocalDateTime triggerTime;
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventNo() {
        return eventNo;
    }

    public void setEventNo(String eventNo) {
        this.eventNo = eventNo;
    }

    public Long getTunnelId() {
        return tunnelId;
    }

    public void setTunnelId(Long tunnelId) {
        this.tunnelId = tunnelId;
    }

    public Long getPointId() {
        return pointId;
    }

    public void setPointId(Long pointId) {
        this.pointId = pointId;
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

    public Integer getWarnLevel() {
        return warnLevel;
    }

    public void setWarnLevel(Integer warnLevel) {
        this.warnLevel = warnLevel;
    }

    public String getWarnTitle() {
        return warnTitle;
    }

    public void setWarnTitle(String warnTitle) {
        this.warnTitle = warnTitle;
    }

    public String getWarnContent() {
        return warnContent;
    }

    public void setWarnContent(String warnContent) {
        this.warnContent = warnContent;
    }

    public Integer getWarnStatus() {
        return warnStatus;
    }

    public void setWarnStatus(Integer warnStatus) {
        this.warnStatus = warnStatus;
    }

    public Integer getGateStage() {
        return gateStage;
    }

    public void setGateStage(Integer gateStage) {
        this.gateStage = gateStage;
    }

    public Long getHazardEventId() {
        return hazardEventId;
    }

    public void setHazardEventId(Long hazardEventId) {
        this.hazardEventId = hazardEventId;
    }

    public String getTriggerValue() {
        return triggerValue;
    }

    public void setTriggerValue(String triggerValue) {
        this.triggerValue = triggerValue;
    }

    public LocalDateTime getTriggerTime() {
        return triggerTime;
    }

    public void setTriggerTime(LocalDateTime triggerTime) {
        this.triggerTime = triggerTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
