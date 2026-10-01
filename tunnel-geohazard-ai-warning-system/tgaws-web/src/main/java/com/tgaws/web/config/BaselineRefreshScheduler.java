package com.tgaws.web.config;

import com.tgaws.compute.judge.PointJudgeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * CUSUM 基线刷新调度（评审 3.4：定时器归 web；对应 μ₀ 每 6h 自适应更新）。
 */
@Component
public class BaselineRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(BaselineRefreshScheduler.class);

    private final PointJudgeManager judgeManager;

    public BaselineRefreshScheduler(PointJudgeManager judgeManager) {
        this.judgeManager = judgeManager;
    }

    @Scheduled(fixedDelay = 6L * 3600_000L, initialDelay = 3600_000L)
    public void refresh() {
        try {
            judgeManager.refreshBaselines();
        } catch (Exception e) {
            log.error("CUSUM 基线刷新失败（D0007 口径）", e);
        }
    }
}
