package com.tgaws.business.warn.mapper;

import com.tgaws.business.warn.entity.HazardEventEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 灾害险情登记 Mapper（warn_hazard_event：FR-407 模型评估真值锚点）。
 */
public interface HazardEventMapper {

    int insert(HazardEventEntity entity);

    HazardEventEntity selectById(@Param("id") long id);

    List<HazardEventEntity> selectByTunnel(@Param("tunnelId") long tunnelId,
                                           @Param("hazardType") Integer hazardType,
                                           @Param("from") java.time.LocalDateTime from,
                                           @Param("to") java.time.LocalDateTime to);
}
