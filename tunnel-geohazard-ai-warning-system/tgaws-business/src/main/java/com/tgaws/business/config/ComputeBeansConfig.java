package com.tgaws.business.config;

import com.tgaws.common.store.IForecastStore;
import com.tgaws.compute.forecast.ForecastBatchTask;
import com.tgaws.compute.forecast.ForecastService;
import com.tgaws.compute.judge.PointJudgeManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 计算层组件装配（business → compute 合法依赖；web 调度器经本 Bean 注入）。
 *
 * <p>定时驱动归 web（评审 3.4）：compute 只暴露方法（refreshBaselines/forecast.run），
 * 本配置仅装配实例，不起任何定时器。</p>
 */
@Configuration
public class ComputeBeansConfig {

    @Bean
    public PointJudgeManager pointJudgeManager() {
        return new PointJudgeManager();
    }

    @Bean
    public ForecastService forecastService() {
        return new ForecastService(1440, 10080);
    }

    @Bean
    public ForecastBatchTask forecastBatchTask(ForecastService forecastService,
                                               IForecastStore forecastStore) {
        return new ForecastBatchTask(forecastService, forecastStore);
    }
}
