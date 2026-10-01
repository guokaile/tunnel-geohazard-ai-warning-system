package com.tgaws.business.mon.entity;

/**
 * 预警规则实体（mon_rule 行）。
 */
public class RuleEntity {

    private Long id;
    private String ruleCode;
    private String ruleName;
    private Integer hazardType;
    private Integer stage;
    private Long sectionId;
    private Integer version;
    private String remark;
    private Integer ruleType;
    private Integer warnLevel;
    private String expressionJson;
    private Integer priority;
    private Integer status;
    private Integer itemType;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public Integer getRuleType() {
        return ruleType;
    }

    public void setRuleType(Integer ruleType) {
        this.ruleType = ruleType;
    }

    public Integer getWarnLevel() {
        return warnLevel;
    }

    public void setWarnLevel(Integer warnLevel) {
        this.warnLevel = warnLevel;
    }

    public String getExpressionJson() {
        return expressionJson;
    }

    public void setExpressionJson(String expressionJson) {
        this.expressionJson = expressionJson;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getItemType() {
        return itemType;
    }

    public void setItemType(Integer itemType) {
        this.itemType = itemType;
    }

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public Integer getHazardType() { return hazardType; }
    public void setHazardType(Integer hazardType) { this.hazardType = hazardType; }
    public Integer getStage() { return stage; }
    public void setStage(Integer stage) { this.stage = stage; }
    public Long getSectionId() { return sectionId; }
    public void setSectionId(Long sectionId) { this.sectionId = sectionId; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
