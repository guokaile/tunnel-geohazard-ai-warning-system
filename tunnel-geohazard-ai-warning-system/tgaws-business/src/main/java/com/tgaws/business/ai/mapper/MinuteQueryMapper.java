package com.tgaws.business.ai.mapper;

import com.tgaws.business.ai.entity.MinuteRow;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分钟聚合查询 Mapper（data_sample_minute：统计通道/预测批算的窗口数据源）。
 */
public interface MinuteQueryMapper {

    /** 查窗口内全部点位分钟样本（预测批算输入；大窗口需 W6 降采样优化） */
    List<MinuteRow> selectWindowForAllPoints(@Param("fromTs") LocalDateTime fromTs);
}
