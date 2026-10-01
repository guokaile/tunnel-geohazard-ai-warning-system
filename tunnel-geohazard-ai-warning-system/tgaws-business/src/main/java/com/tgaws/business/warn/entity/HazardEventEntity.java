package com.tgaws.business.warn.entity;

import java.time.LocalDateTime;

/**
 * 灾害险情登记实体（warn_hazard_event 行）。
 */
public class HazardEventEntity {

    private Long id;
    private String hazardNo;
    private Long tunnelId;
    private Long sectionId;
    private Integer hazardType;
    private LocalDateTime eventTime;
    private String position;
    private String consequence;
    private Integer level;
    private Long relateEventId;
    private Long patrolHazardId;
    private Long registerUserId;
    private String remark;

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

    public Integer getHazardType() {
        return hazardType;
    }

    public void setHazardType(Integer hazardType) {
        this.hazardType = hazardType;
    }

    public LocalDateTime getEventTime() {
        return eventTime;
    }

    public void setEventTime(LocalDateTime eventTime) {
        this.eventTime = eventTime;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getConsequence() {
        return consequence;
    }

    public void setConsequence(String consequence) {
        this.consequence = consequence;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public Long getRelateEventId() {
        return relateEventId;
    }

    public void setRelateEventId(Long relateEventId) {
        this.relateEventId = relateEventId;
    }

    public Long getPatrolHazardId() {
        return patrolHazardId;
    }

    public void setPatrolHazardId(Long patrolHazardId) {
        this.patrolHazardId = patrolHazardId;
    }

    public Long getRegisterUserId() {
        return registerUserId;
    }

    public void setRegisterUserId(Long registerUserId) {
        this.registerUserId = registerUserId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
