package com.tgaws.business.patrol.entity;

/**
 * 巡检计划实体（patrol_plan 行，FR-501 按班次/日/周自动生成任务）。
 */
public class PatrolPlanEntity {

    private Long id;
    private String planNo;
    private String planName;
    private Long tunnelId;
    private Integer frequencyType;
    private String timeSlot;
    private Long templateId;
    private Long inspectorId;
    private Integer enabled;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPlanNo() {
        return planNo;
    }

    public void setPlanNo(String planNo) {
        this.planNo = planNo;
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public Long getTunnelId() {
        return tunnelId;
    }

    public void setTunnelId(Long tunnelId) {
        this.tunnelId = tunnelId;
    }

    public Integer getFrequencyType() {
        return frequencyType;
    }

    public void setFrequencyType(Integer frequencyType) {
        this.frequencyType = frequencyType;
    }

    public String getTimeSlot() {
        return timeSlot;
    }

    public void setTimeSlot(String timeSlot) {
        this.timeSlot = timeSlot;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public Long getInspectorId() {
        return inspectorId;
    }

    public void setInspectorId(Long inspectorId) {
        this.inspectorId = inspectorId;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
