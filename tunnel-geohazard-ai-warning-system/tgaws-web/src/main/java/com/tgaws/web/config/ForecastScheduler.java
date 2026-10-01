package com.tgaws.web.config;

import com.tgaws.common.rule.PointSample;
import com.tgaws.common.store.ISampleWindowProvider;
import com.tgaws.compute.forecast.ForecastBatchTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 预测批算调度（评审 3.4：定时器归 web，compute 只暴露 ForecastBatchTask.run）。
 *
 * <p>每 5min 批算 → ai_forecast（ON DUPLICATE 幂等）；窗口 180 分钟为接线验证口径，
 * 长窗口降采样优化列入 W6（大窗口全量查询需避免击穿性能）。</p>
 */
@Component
public class ForecastScheduler {

    private static final Logger log = LoggerFactory.getLogger(ForecastScheduler.class);

    /** 窗口（分钟）：接线验证口径 */
    private static final int WINDOW_MINUTES = 180;

    private final ForecastBatchTask forecastBatchTask;
    private final ISampleWindowProvider windowProvider;

    public ForecastScheduler(ForecastBatchTask forecastBatchTask, ISampleWindowProvider windowProvider) {
        this.forecastBatchTask = forecastBatchTask;
        this.windowProvider = windowProvider;
    }

    @Scheduled(fixedDelay = 300_000L, initialDelay = 60_000L)
    public void runBatch() {
        try {
            Map<Long, List<PointSample>> windows = windowProvider.minuteWindowForAllPoints(WINDOW_MINUTES);
            forecastBatchTask.run(windows);
        } catch (Exception e) {
            log.error("预测批算失败（D0007 口径）", e);
        }
    }
}
