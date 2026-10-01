package com.tgaws.business.mon.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 监控域查询 VO 集合（T-811：B05/B16~B20 实时监控中心数据出口）。
 *
 * <p>MyBatis resultType 直接映射（驼峰别名），数值类型与《3》表结构一致。</p>
 */
public final class MonQueryVos {

    private MonQueryVos() {
    }

    /** B16 点位实时最新值（联查 mon_point + data_point_latest + 未消警事件级别） */
    public static class PointLatestVo {

        private Long pointId;
        private String pointCode;
        private String pointName;
        private Long tunnelId;
        private String tunnelName;
        private Long sectionId;
        private Integer hazardType;
        private Integer itemType;
        private String unit;
        private BigDecimal value;
        private LocalDateTime ts;
        private Integer quality;
        private Integer onlineStatus;
        /** 当前未消警事件最高级别（1蓝2黄3橙4红；null=无预警），看板色标依据 */
        private Integer warnLevel;

        public Long getPointId() {
            return pointId;
        }

        public void setPointId(Long pointId) {
            this.pointId = pointId;
        }

        public String getPointCode() {
            return pointCode;
        }

        public void setPointCode(String pointCode) {
            this.pointCode = pointCode;
        }

        public String getPointName() {
            return pointName;
        }

        public void setPointName(String pointName) {
            this.pointName = pointName;
        }

        public Long getTunnelId() {
            return tunnelId;
        }

        public void setTunnelId(Long tunnelId) {
            this.tunnelId = tunnelId;
        }

        public String getTunnelName() {
            return tunnelName;
        }

        public void setTunnelName(String tunnelName) {
            this.tunnelName = tunnelName;
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

        public Integer getItemType() {
            return itemType;
        }

        public void setItemType(Integer itemType) {
            this.itemType = itemType;
        }

        public String getUnit() {
            return unit;
        }

        public void setUnit(String unit) {
            this.unit = unit;
        }

        public BigDecimal getValue() {
            return value;
        }

        public void setValue(BigDecimal value) {
            this.value = value;
        }

        public LocalDateTime getTs() {
            return ts;
        }

        public void setTs(LocalDateTime ts) {
            this.ts = ts;
        }

        public Integer getQuality() {
            return quality;
        }

        public void setQuality(Integer quality) {
            this.quality = quality;
        }

        public Integer getOnlineStatus() {
            return onlineStatus;
        }

        public void setOnlineStatus(Integer onlineStatus) {
            this.onlineStatus = onlineStatus;
        }

        public Integer getWarnLevel() {
            return warnLevel;
        }

        public void setWarnLevel(Integer warnLevel) {
            this.warnLevel = warnLevel;
        }
    }

    /** B17 时序数据点（raw 与 minute 共用；minute 粒度为窗口均值） */
    public static class SeriesPointVo {

        private LocalDateTime ts;
        private BigDecimal value;
        private Integer quality;

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
    }

    /** B18 统计值（max/min/avg/rate：末值-首值 / 时间跨度） */
    public static class PointStatsVo {

        private BigDecimal max;
        private BigDecimal min;
        private BigDecimal avg;
        private BigDecimal rate;

        public BigDecimal getMax() {
            return max;
        }

        public void setMax(BigDecimal max) {
            this.max = max;
        }

        public BigDecimal getMin() {
            return min;
        }

        public void setMin(BigDecimal min) {
            this.min = min;
        }

        public BigDecimal getAvg() {
            return avg;
        }

        public void setAvg(BigDecimal avg) {
            this.avg = avg;
        }

        public BigDecimal getRate() {
            return rate;
        }

        public void setRate(BigDecimal rate) {
            this.rate = rate;
        }
    }

    /** B19 断面图：断面元信息 + 点位分布实时值 */
    public static class SectionBoardVo {

        private Long sectionId;
        private String sectionCode;
        private String sectionName;
        private String mileageFrom;
        private String mileageTo;
        private String geoZone;
        private List<PointLatestVo> points;

        public Long getSectionId() {
            return sectionId;
        }

        public void setSectionId(Long sectionId) {
            this.sectionId = sectionId;
        }

        public String getSectionCode() {
            return sectionCode;
        }

        public void setSectionCode(String sectionCode) {
            this.sectionCode = sectionCode;
        }

        public String getSectionName() {
            return sectionName;
        }

        public void setSectionName(String sectionName) {
            this.sectionName = sectionName;
        }

        public String getMileageFrom() {
            return mileageFrom;
        }

        public void setMileageFrom(String mileageFrom) {
            this.mileageFrom = mileageFrom;
        }

        public String getMileageTo() {
            return mileageTo;
        }

        public void setMileageTo(String mileageTo) {
            this.mileageTo = mileageTo;
        }

        public String getGeoZone() {
            return geoZone;
        }

        public void setGeoZone(String geoZone) {
            this.geoZone = geoZone;
        }

        public List<PointLatestVo> getPoints() {
            return points;
        }

        public void setPoints(List<PointLatestVo> points) {
            this.points = points;
        }
    }

    /** B20 概览统计（数据范围过滤后） */
    public static class OverviewVo {

        private long totalPoints;
        private long onlinePoints;
        private long offlinePoints;
        private long todaySamples;
        /** 在线率（0~100，一位小数） */
        private BigDecimal onlineRate;

        public long getTotalPoints() {
            return totalPoints;
        }

        public void setTotalPoints(long totalPoints) {
            this.totalPoints = totalPoints;
        }

        public long getOnlinePoints() {
            return onlinePoints;
        }

        public void setOnlinePoints(long onlinePoints) {
            this.onlinePoints = onlinePoints;
        }

        public long getOfflinePoints() {
            return offlinePoints;
        }

        public void setOfflinePoints(long offlinePoints) {
            this.offlinePoints = offlinePoints;
        }

        public long getTodaySamples() {
            return todaySamples;
        }

        public void setTodaySamples(long todaySamples) {
            this.todaySamples = todaySamples;
        }

        public BigDecimal getOnlineRate() {
            return onlineRate;
        }

        public void setOnlineRate(BigDecimal onlineRate) {
            this.onlineRate = onlineRate;
        }
    }
}
