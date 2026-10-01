package com.tgaws.business.ai.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预测结果实体（ai_forecast 行）。
 */
public class ForecastEntity {

    private Long pointId;
    private Long modelId;
    private LocalDateTime forecastAt;
    private LocalDateTime targetTime;
    private BigDecimal forecastValue;
    private BigDecimal lowerBound;
    private BigDecimal upperBound;

    public Long getPointId() {
        return pointId;
    }

    public void setPointId(Long pointId) {
        this.pointId = pointId;
    }

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public LocalDateTime getForecastAt() {
        return forecastAt;
    }

    public void setForecastAt(LocalDateTime forecastAt) {
        this.forecastAt = forecastAt;
    }

    public LocalDateTime getTargetTime() {
        return targetTime;
    }

    public void setTargetTime(LocalDateTime targetTime) {
        this.targetTime = targetTime;
    }

    public BigDecimal getForecastValue() {
        return forecastValue;
    }

    public void setForecastValue(BigDecimal forecastValue) {
        this.forecastValue = forecastValue;
    }

    public BigDecimal getLowerBound() {
        return lowerBound;
    }

    public void setLowerBound(BigDecimal lowerBound) {
        this.lowerBound = lowerBound;
    }

    public BigDecimal getUpperBound() {
        return upperBound;
    }

    public void setUpperBound(BigDecimal upperBound) {
        this.upperBound = upperBound;
    }
}
