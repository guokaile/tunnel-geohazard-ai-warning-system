package com.tgaws.business.mon.mapper;

import com.tgaws.business.mon.entity.PointBasicEntity;
import org.apache.ibatis.annotations.Param;

/**
 * 监测点位 Mapper（mon_point 台账）。
 */
public interface PointMapper {

    /** 按点位编码查启用点位 id（未配置/已删除返回 null） */
    Long selectIdByCode(@Param("pointCode") String pointCode);

    /** 按点位 id 查基础信息（判定编排用） */
    PointBasicEntity selectBasicById(@Param("pointId") long pointId);
}
