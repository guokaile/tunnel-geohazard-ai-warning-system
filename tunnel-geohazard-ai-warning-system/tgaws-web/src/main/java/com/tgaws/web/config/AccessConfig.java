package com.tgaws.web.config;

import com.tgaws.access.mqtt.MqttBrokerStarter;
import com.tgaws.access.tcp.TcpServer;
import com.tgaws.access.tcp.auth.GatewayOnlineRegistry;
import com.tgaws.common.gateway.IGatewaySecretProvider;
import com.tgaws.common.pipeline.IDataPipeline;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 接入层装配（W4-4c 接线，对应《6》6.3.3 离线迁移归属）：
 *
 * <p>①GatewayOnlineRegistry 常驻 Bean（离线扫描与 D0006 数据中断检测的数据源）；
 * ②TCP 服务/内嵌 MQTT 按配置开关启动（默认关闭，避免开发环境抢端口）；
 * ③定时扫描（GatewayOnlineScanner）驱动 在线→离线 迁移与告警。</p>
 */
@Configuration
@EnableScheduling
public class AccessConfig {

    private static final Logger log = LoggerFactory.getLogger(AccessConfig.class);

    private TcpServer tcpServer;
    private MqttBrokerStarter mqttBroker;

    /** 网关在线注册表（常驻；TCP 服务未启用时为空表，扫描任务空转） */
    @Bean
    public GatewayOnlineRegistry gatewayOnlineRegistry() {
        return new GatewayOnlineRegistry();
    }

    /** TCP 采集服务（配置 tgaws.access.tcp.enabled=true 启动；需 IDataPipeline 与凭据提供已装配；
     *  共享在线注册表 Bean——心跳数据进入离线扫描器视图，杜绝双实例漂移） */
    @Bean
    @ConditionalOnProperty(name = "tgaws.access.tcp.enabled", havingValue = "true")
    public TcpServer tcpServer(IDataPipeline pipeline,
                               IGatewaySecretProvider secretProvider,
                               GatewayOnlineRegistry onlineRegistry) {
        tcpServer = new TcpServer(TcpServer.DEFAULT_PORT, pipeline, secretProvider, 4, onlineRegistry);
        return tcpServer;
    }

    /** 内嵌 MQTT Broker（配置 tgaws.access.mqtt.enabled=true 启动） */
    @Bean
    @ConditionalOnProperty(name = "tgaws.access.mqtt.enabled", havingValue = "true")
    public MqttBrokerStarter mqttBrokerStarter() {
        mqttBroker = new MqttBrokerStarter();
        return mqttBroker;
    }

    /** 应用就绪后启动接入服务（端口绑定失败抛异常，由装配层决策重试） */
    @EventListener(ApplicationReadyEvent.class)
    public void startServers() throws Exception {
        if (tcpServer != null) {
            tcpServer.start();
        }
        if (mqttBroker != null) {
            mqttBroker.start();
        }
        log.info("接入层装配完成：TCP={} MQTT={}", tcpServer != null, mqttBroker != null);
    }

    @PreDestroy
    public void stopServers() {
        if (tcpServer != null) {
            tcpServer.stop();
        }
        if (mqttBroker != null) {
            mqttBroker.stop();
        }
    }
}
