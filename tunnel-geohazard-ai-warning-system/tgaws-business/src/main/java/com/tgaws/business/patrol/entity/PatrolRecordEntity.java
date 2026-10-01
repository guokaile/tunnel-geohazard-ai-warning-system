package com.tgaws.business.patrol.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 巡检记录实体（patrol_record 行；client_key 离线补传幂等键，评审 3.3）。
 */
public class PatrolRecordEntity {

    private Long id;
    private Long taskId;
    private String clientKey;
    private Long itemId;
    private String itemName;
    private String judgeStandardSnapshot;
    private Integer result;
    private String description;
    private String images;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private LocalDateTime recordTime;
    private Long recorderId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getClientKey() {
        return clientKey;
    }

    public void setClientKey(String clientKey) {
        this.clientKey = clientKey;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getJudgeStandardSnapshot() {
        return judgeStandardSnapshot;
    }

    public void setJudgeStandardSnapshot(String judgeStandardSnapshot) {
        this.judgeStandardSnapshot = judgeStandardSnapshot;
    }

    public Integer getResult() {
        return result;
    }

    public void setResult(Integer result) {
        this.result = result;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImages() {
        return images;
    }

    public void setImages(String images) {
        this.images = images;
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

    public LocalDateTime getRecordTime() {
        return recordTime;
    }

    public void setRecordTime(LocalDateTime recordTime) {
        this.recordTime = recordTime;
    }

    public Long getRecorderId() {
        return recorderId;
    }

    public void setRecorderId(Long recorderId) {
        this.recorderId = recorderId;
    }
}
