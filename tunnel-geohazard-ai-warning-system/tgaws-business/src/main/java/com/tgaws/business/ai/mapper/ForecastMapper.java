package com.tgaws.business.ai.mapper;

import com.tgaws.business.ai.entity.ForecastEntity;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 预测结果 Mapper（ai_forecast：uk_point_target 幂等覆盖 + 90 天清理）。
 */
public interface ForecastMapper {

    /** 批量 upsert（ON DUPLICATE KEY UPDATE 幂等覆盖批算重跑） */
    int batchUpsert(@Param("rows") List<ForecastEntity> rows);

    /** 分批删除超期记录（防长事务，单批上限 10000） */
    int deleteBefore(@Param("forecastAt") LocalDateTime forecastAt, @Param("limit") int limit);
}
