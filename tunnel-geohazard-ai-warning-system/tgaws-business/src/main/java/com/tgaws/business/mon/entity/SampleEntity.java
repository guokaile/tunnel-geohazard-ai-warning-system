package com.tgaws.business.mon.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采样实体（data_sample 行，业务层内部对象；ts 已由毫秒转换为 LocalDateTime）。
 */
public class SampleEntity {

    private Long pointId;
    private LocalDateTime ts;
    private BigDecimal value;
    private Integer quality;
    private Integer source;
    private LocalDateTime receiveTime;

    public Long getPointId() {
        return pointId;
    }

    public void setPointId(Long pointId) {
        this.pointId = pointId;
    }

    public LocalDateTime getTs() {
        return ts;
    }

    public void setTs(LocalDateTime ts) {
        this.ts = ts;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public Integer getQuality() {
        return quality;
    }

    public void setQuality(Integer quality) {
        this.quality = quality;
    }

    public Integer getSource() {
        return source;
    }

    public void setSource(Integer source) {
        this.source = source;
    }

    public LocalDateTime getReceiveTime() {
        return receiveTime;
    }

    public void setReceiveTime(LocalDateTime receiveTime) {
        this.receiveTime = receiveTime;
    }
}
