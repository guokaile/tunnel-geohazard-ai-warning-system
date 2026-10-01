package com.tgaws.business.rpt.mapper;

import com.tgaws.business.rpt.entity.RptReportEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 分析报告记录 Mapper（rpt_report，FR-605）。
 */
public interface RptReportMapper {

    int insert(RptReportEntity entity);

    RptReportEntity selectById(@Param("id") long id);

    List<RptReportEntity> selectList(@Param("reportType") Integer reportType,
                                     @Param("period") String period);
}
