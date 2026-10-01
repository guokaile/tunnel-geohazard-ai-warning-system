package com.tgaws.business.ai.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 分钟聚合行（data_sample_minute 查询结果）。
 */
public class MinuteRow {

    private Long pointId;
    private LocalDateTime tsMinute;
    private BigDecimal avgValue;

    public Long getPointId() {
        return pointId;
    }

    public void setPointId(Long pointId) {
        this.pointId = pointId;
    }

    public LocalDateTime getTsMinute() {
        return tsMinute;
    }

    public void setTsMinute(LocalDateTime tsMinute) {
        this.tsMinute = tsMinute;
    }

    public BigDecimal getAvgValue() {
        return avgValue;
    }

    public void setAvgValue(BigDecimal avgValue) {
        this.avgValue = avgValue;
    }
}
