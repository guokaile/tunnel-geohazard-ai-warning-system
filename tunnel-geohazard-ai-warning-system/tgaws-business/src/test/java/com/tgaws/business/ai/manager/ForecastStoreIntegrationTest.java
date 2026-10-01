package com.tgaws.business.ai.manager;

import com.tgaws.business.SampleStoreTestConfig;
import com.tgaws.common.store.ForecastRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 预测结果真实库集成测试（tgaws_test）：
 * ①uk_point_target 幂等覆盖（批算重跑）②90 天清理分批删除。
 */
@SpringBootTest(classes = SampleStoreTestConfig.class)
@ActiveProfiles("test")
@Transactional
class ForecastStoreIntegrationTest {

    @Autowired
    private ForecastStoreManager forecastStoreManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM ai_forecast");
    }

    @Test
    void upsertIsIdempotentPerBatch() {
        long now = System.currentTimeMillis();
        ForecastRow row1 = new ForecastRow(1L, 0L, now, now + 300_000L,
                BigDecimal.valueOf(10.0), BigDecimal.valueOf(9.0), BigDecimal.valueOf(11.0));
        forecastStoreManager.batchUpsert(List.of(row1));
        // 批算重跑：同 (point_id,target_time,forecast_at) 新值覆盖
        ForecastRow row2 = new ForecastRow(1L, 0L, now, now + 300_000L,
                BigDecimal.valueOf(12.0), BigDecimal.valueOf(11.0), BigDecimal.valueOf(13.0));
        forecastStoreManager.batchUpsert(List.of(row2));

        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_forecast WHERE point_id=1", Integer.class);
        assertEquals(1, cnt, "批算重跑不得产生重复行");
        BigDecimal v = jdbcTemplate.queryForObject(
                "SELECT forecast_value FROM ai_forecast WHERE point_id=1", BigDecimal.class);
        assertEquals(0, new BigDecimal("12.0").compareTo(v), "重跑后写覆盖前值");
    }

    @Test
    void deleteBeforeRemovesExpiredOnly() {
        long now = System.currentTimeMillis();
        long oldTs = now - 100L * 24 * 60 * 60 * 1000L; // 100 天前
        ForecastRow oldRow = new ForecastRow(2L, 0L, oldTs, oldTs + 300_000L,
                BigDecimal.valueOf(1.0), null, null);
        ForecastRow newRow = new ForecastRow(3L, 0L, now, now + 300_000L,
                BigDecimal.valueOf(2.0), null, null);
        forecastStoreManager.batchUpsert(List.of(oldRow, newRow));

        forecastStoreManager.deleteBefore(now - 90L * 24 * 60 * 60 * 1000L);

        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_forecast", Integer.class);
        assertEquals(1, remaining, "仅保留 90 天窗口内记录");
        Integer pointId = jdbcTemplate.queryForObject(
                "SELECT point_id FROM ai_forecast", Integer.class);
        assertEquals(3, pointId);
    }
}
