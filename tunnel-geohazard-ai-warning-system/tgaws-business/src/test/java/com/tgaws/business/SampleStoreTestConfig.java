package com.tgaws.business;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 集成测试最小装配（W4-4c）：数据源自动配置 + MyBatis Mapper 扫描 + business 组件扫描
 * （含 mon/ai 各 manager 契约实现）。
 */
@Configuration
@EnableAutoConfiguration
@MapperScan({"com.tgaws.business.mon.mapper", "com.tgaws.business.ai.mapper",
        "com.tgaws.business.warn.mapper", "com.tgaws.business.sys.mapper",
        "com.tgaws.business.patrol.mapper", "com.tgaws.business.rpt.mapper",
        "com.tgaws.business.data.mapper"})
@ComponentScan("com.tgaws.business")
public class SampleStoreTestConfig {
}
