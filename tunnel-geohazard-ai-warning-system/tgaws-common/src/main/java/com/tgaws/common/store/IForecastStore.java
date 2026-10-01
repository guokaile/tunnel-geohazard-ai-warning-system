package com.tgaws.common.store;

import java.util.List;

/**
 * 预测结果存储契约（ai_forecast：批算落库 90 天，回测与 API-E05 查询数据源）。
 *
 * <p>契约层位于 common；实现（business，ON DUPLICATE KEY UPDATE 幂等）由 web 装配注入。</p>
 */
public interface IForecastStore {

    /**
     * 批量写入预测结果（uk_point_target(point_id,target_time,forecast_at) 幂等覆盖，
     * 批算重跑不产生重复行）。
     */
    void batchUpsert(List<ForecastRow> rows);

    /**
     * 清理 forecast_at 早于阈值的记录（90 天保留期，每日清理任务驱动；分批删除防长事务）。
     *
     * @param forecastAtBeforeMs 阈值（Unix 毫秒）
     */
    void deleteBefore(long forecastAtBeforeMs);
}
