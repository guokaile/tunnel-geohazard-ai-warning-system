package com.tgaws.web.config;

import com.tgaws.business.mon.manager.SampleStoreManager;
import com.tgaws.common.pipeline.IDataPipeline;
import com.tgaws.compute.pipeline.DisruptorPipeline;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 数据管道装配（W4-4c）：common 契约（ISampleStore/IPointLatestStore/IPointResolver）
 * 统一由 business 的 SampleStoreManager 实现，web 装配注入 compute 管道。
 */
@Configuration
public class PipelineConfig {

    private DisruptorPipeline pipeline;

    @Bean
    public IDataPipeline dataPipeline(SampleStoreManager storeManager) {
        pipeline = new DisruptorPipeline(
                DisruptorPipeline.DEFAULT_RING_SIZE,
                storeManager,      // ISampleStore
                storeManager,      // IPointLatestStore
                storeManager);     // IPointResolver
        pipeline.start();
        return pipeline;
    }

    @PreDestroy
    public void stopPipeline() {
        if (pipeline != null) {
            pipeline.stop();
        }
    }
}
