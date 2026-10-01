package com.tgaws.compute.forecast;

import com.tgaws.common.store.ForecastRow;
import com.tgaws.common.store.IForecastStore;
import com.tgaws.common.rule.PointSample;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 预测批算任务单测（暖机点位落库/冷启动点位跳过/行数=暖机点位×6）。
 */
class ForecastBatchTaskTest {

    @Test
    void batchTaskWritesOnlyWarmPoints() {
        List<ForecastRow> written = new ArrayList<>();
        IForecastStore stub = new IForecastStore() {
            @Override
            public void batchUpsert(List<ForecastRow> rows) {
                written.addAll(rows);
            }

            @Override
            public void deleteBefore(long forecastAtBeforeMs) {
            }
        };
        ForecastBatchTask task = new ForecastBatchTask(new ForecastService(1440, 10080), stub);
        long now = System.currentTimeMillis();
        // 点位 1：暖机（1500 样本）；点位 2：冷启动（100 样本）
        Map<Long, List<PointSample>> samplesByPoint = Map.of(
                1L, samples(1500, 10.0D),
                2L, samples(100, 10.0D));
        int rows = task.run(samplesByPoint);
        assertEquals(6, rows, "仅暖机点位落库：1 点 × 6 行");
        assertTrue(written.stream().allMatch(r -> r.pointId() == 1L), "冷启动点位不得写入");
        assertEquals(ForecastBatchTask.BUILTIN_MODEL_ID, written.get(0).modelId());
        assertTrue(written.get(0).forecastAtMs() <= now + 1000 && written.get(0).forecastAtMs() >= now - 1000,
                "forecast_at 为批算时刻");
    }

    private static List<PointSample> samples(int n, double base) {
        List<PointSample> list = new ArrayList<>(n);
        long baseTs = System.currentTimeMillis() - n * 60_000L;
        for (int i = 0; i < n; i++) {
            list.add(new PointSample(baseTs + i * 60_000L, BigDecimal.valueOf(base + i * 0.001D), 0));
        }
        return list;
    }
}
