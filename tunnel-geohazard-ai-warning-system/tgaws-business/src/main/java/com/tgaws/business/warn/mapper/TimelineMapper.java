package com.tgaws.business.warn.mapper;

import com.tgaws.business.warn.entity.TimelineEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 预警事件时间线 Mapper（warn_event_timeline：仅追加，无 update/delete——FR-406 不可篡改落地点）。
 */
public interface TimelineMapper {

    int insert(TimelineEntity entity);

    List<TimelineEntity> selectByEvent(@Param("eventId") long eventId);
}
