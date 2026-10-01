package com.tgaws.business.rpt.entity;

import java.time.LocalDate;

/**
 * 报表日聚合行（rpt_stat_daily，FR-801/802 预聚合口径：
 * 完整日全量聚合 + 当日实时段小查询——FR-802 ≤1min 刷新的物理前提）。
 */
public class RptStatDailyEntity {

    private Long id;
    private Long tunnelId;
    private LocalDate statDate;
    private Integer warnTotal;
    private Integer warnRed;
    private Integer warnConfirmed;
    private Integer warnClosed;
    private Integer disposeTotal;
    private Integer disposeClosed;
    private Integer patrolTotal;
    private Integer patrolDone;
    private Integer hazardNew;
    private Integer hazardClosed;
    private Long sampleCount;
    private Long sampleExpect;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTunnelId() {
        return tunnelId;
    }

    public void setTunnelId(Long tunnelId) {
        this.tunnelId = tunnelId;
    }

    public LocalDate getStatDate() {
        return statDate;
    }

    public void setStatDate(LocalDate statDate) {
        this.statDate = statDate;
    }

    public Integer getWarnTotal() {
        return warnTotal;
    }

    public void setWarnTotal(Integer warnTotal) {
        this.warnTotal = warnTotal;
    }

    public Integer getWarnRed() {
        return warnRed;
    }

    public void setWarnRed(Integer warnRed) {
        this.warnRed = warnRed;
    }

    public Integer getWarnConfirmed() {
        return warnConfirmed;
    }

    public void setWarnConfirmed(Integer warnConfirmed) {
        this.warnConfirmed = warnConfirmed;
    }

    public Integer getWarnClosed() {
        return warnClosed;
    }

    public void setWarnClosed(Integer warnClosed) {
        this.warnClosed = warnClosed;
    }

    public Integer getDisposeTotal() {
        return disposeTotal;
    }

    public void setDisposeTotal(Integer disposeTotal) {
        this.disposeTotal = disposeTotal;
    }

    public Integer getDisposeClosed() {
        return disposeClosed;
    }

    public void setDisposeClosed(Integer disposeClosed) {
        this.disposeClosed = disposeClosed;
    }

    public Integer getPatrolTotal() {
        return patrolTotal;
    }

    public void setPatrolTotal(Integer patrolTotal) {
        this.patrolTotal = patrolTotal;
    }

    public Integer getPatrolDone() {
        return patrolDone;
    }

    public void setPatrolDone(Integer patrolDone) {
        this.patrolDone = patrolDone;
    }

    public Integer getHazardNew() {
        return hazardNew;
    }

    public void setHazardNew(Integer hazardNew) {
        this.hazardNew = hazardNew;
    }

    public Integer getHazardClosed() {
        return hazardClosed;
    }

    public void setHazardClosed(Integer hazardClosed) {
        this.hazardClosed = hazardClosed;
    }

    public Long getSampleCount() {
        return sampleCount;
    }

    public void setSampleCount(Long sampleCount) {
        this.sampleCount = sampleCount;
    }

    public Long getSampleExpect() {
        return sampleExpect;
    }

    public void setSampleExpect(Long sampleExpect) {
        this.sampleExpect = sampleExpect;
    }
}
