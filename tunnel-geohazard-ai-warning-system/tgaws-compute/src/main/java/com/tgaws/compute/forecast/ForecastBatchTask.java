package com.tgaws.compute.forecast;

import com.tgaws.common.store.ForecastRow;
import com.tgaws.common.store.IForecastStore;
import com.tgaws.common.rule.PointSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 预测批算任务（《5》5.5.3：每 5min 批算全部启用点位，结果落 ai_forecast 保留 90 天，
 * 支撑模型评估回测与 API-E05 查询——查询不触发实时计算）。
 *
 * <p>内置统计模型 modelId=0（未注册占位）。</p>
 */
public class ForecastBatchTask {

    private static final Logger log = LoggerFactory.getLogger(ForecastBatchTask.class);

    /** 内置统计模型占位 id */
    public static final long BUILTIN_MODEL_ID = 0L;

    private final ForecastService forecastService;
    private final IForecastStore store;

    public ForecastBatchTask(ForecastService forecastService, IForecastStore store) {
        this.forecastService = forecastService;
        this.store = store;
    }

    /**
     * 执行批算：按点位分钟窗口样本预测并落库。
     *
     * @param samplesByPoint 点位 → 分钟级窗口样本（时间升序）
     * @return 写入行数
     */
    public int run(Map<Long, List<PointSample>> samplesByPoint) {
        long now = System.currentTimeMillis();
        List<ForecastRow> rows = new ArrayList<>();
        for (Map.Entry<Long, List<PointSample>> entry : samplesByPoint.entrySet()) {
            forecastService.forecast(entry.getKey(), now, entry.getValue())
                    .ifPresent(r -> {
                        for (ForecastService.ForecastPoint p : r.points()) {
                            rows.add(new ForecastRow(
                                    r.pointId(), BUILTIN_MODEL_ID, r.forecastAtMs(), p.targetTimeMs(),
                                    p.value(), p.lower(), p.upper()));
                        }
                    });
        }
        if (!rows.isEmpty()) {
            store.batchUpsert(rows);
        }
        log.info("预测批算完成：暖机点位 {} 个，写入 {} 行",
                rows.size() / (ForecastService.DEFAULT_HORIZON_MINUTES / ForecastService.STEP_MINUTES), rows.size());
        return rows.size();
    }
}
