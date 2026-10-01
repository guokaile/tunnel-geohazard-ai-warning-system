package com.tgaws.business.ai.manager;

import com.tgaws.business.ai.entity.ForecastEntity;
import com.tgaws.business.ai.mapper.ForecastMapper;
import com.tgaws.common.store.ForecastRow;
import com.tgaws.common.store.IForecastStore;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 预测结果存储适配（common IForecastStore 契约 → MyBatis 实现；web 装配注入）。
 */
@Component
public class ForecastStoreManager implements IForecastStore {

    /** 清理分批上限（防长事务） */
    private static final int DELETE_BATCH_LIMIT = 10_000;

    private static final ZoneId DB_ZONE = ZoneId.of("Asia/Shanghai");

    private final ForecastMapper forecastMapper;

    public ForecastStoreManager(ForecastMapper forecastMapper) {
        this.forecastMapper = forecastMapper;
    }

    @Override
    public void batchUpsert(List<ForecastRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<ForecastEntity> entities = new ArrayList<>(rows.size());
        for (ForecastRow row : rows) {
            ForecastEntity entity = new ForecastEntity();
            entity.setPointId(row.pointId());
            entity.setModelId(row.modelId());
            entity.setForecastAt(toLocalDateTime(row.forecastAtMs()));
            entity.setTargetTime(toLocalDateTime(row.targetTimeMs()));
            entity.setForecastValue(row.forecastValue());
            entity.setLowerBound(row.lowerBound());
            entity.setUpperBound(row.upperBound());
            entities.add(entity);
        }
        forecastMapper.batchUpsert(entities);
    }

    @Override
    public void deleteBefore(long forecastAtBeforeMs) {
        LocalDateTime threshold = toLocalDateTime(forecastAtBeforeMs);
        int deleted;
        do {
            deleted = forecastMapper.deleteBefore(threshold, DELETE_BATCH_LIMIT);
        } while (deleted >= DELETE_BATCH_LIMIT);
    }

    private static LocalDateTime toLocalDateTime(long epochMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), DB_ZONE);
    }
}
