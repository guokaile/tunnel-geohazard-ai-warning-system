package com.tgaws.web.config;

import com.tgaws.common.store.IForecastStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ai_forecast 保留期清理（《6》6.3.3：每日 03:30，保留 90 天，与回测窗口一致）。
 */
@Component
public class ForecastCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(ForecastCleanupTask.class);

    /** 保留期（天） */
    private static final long RETENTION_DAYS = 90L;

    private final IForecastStore forecastStore;

    public ForecastCleanupTask(IForecastStore forecastStore) {
        this.forecastStore = forecastStore;
    }

    @Scheduled(cron = "0 30 3 * * ?")
    public void cleanup() {
        long threshold = System.currentTimeMillis() - RETENTION_DAYS * 24 * 60 * 60 * 1000L;
        forecastStore.deleteBefore(threshold);
        log.info("ai_forecast 清理完成：删除 forecast_at 早于 {} 的记录", threshold);
    }
}
