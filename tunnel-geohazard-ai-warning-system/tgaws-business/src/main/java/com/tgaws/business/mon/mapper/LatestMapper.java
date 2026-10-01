package com.tgaws.business.mon.mapper;

import com.tgaws.business.mon.entity.SampleEntity;
import org.apache.ibatis.annotations.Param;

/**
 * 点位最新值 Mapper（data_point_latest 单行/点位，upsert 维护）。
 */
public interface LatestMapper {

    /** upsert 最新值 */
    int upsert(@Param("row") SampleEntity row);
}
