package com.tgaws.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * TGAWS 启动入口（接口与装配层）。
 */
@SpringBootApplication(scanBasePackages = "com.tgaws")
@MapperScan({"com.tgaws.business.mon.mapper", "com.tgaws.business.ai.mapper",
        "com.tgaws.business.warn.mapper", "com.tgaws.business.sys.mapper",
        "com.tgaws.business.patrol.mapper", "com.tgaws.business.rpt.mapper",
        "com.tgaws.business.data.mapper"})
public class TgawsApplication {

    public static void main(String[] args) {
        SpringApplication.run(TgawsApplication.class, args);
    }
}
