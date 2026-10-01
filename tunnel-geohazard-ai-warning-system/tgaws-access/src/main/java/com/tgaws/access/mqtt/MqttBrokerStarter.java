package com.tgaws.access.mqtt;

import io.moquette.BrokerConstants;
import io.moquette.broker.Server;
import io.moquette.broker.config.IConfig;
import io.moquette.broker.config.MemoryConfig;
import io.moquette.interception.InterceptHandler;
import io.moquette.interception.messages.InterceptAcknowledgedMessage;
import io.moquette.interception.messages.InterceptConnectMessage;
import io.moquette.interception.messages.InterceptConnectionLostMessage;
import io.moquette.interception.messages.InterceptDisconnectMessage;
import io.moquette.interception.messages.InterceptPublishMessage;
import io.moquette.interception.messages.InterceptSubscribeMessage;
import io.moquette.interception.messages.InterceptUnsubscribeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内嵌 MQTT Broker（Moquette 0.17，进程内运行随应用启停——《4》4.4.2）。
 *
 * <p>协议版本声明（T3 结论）：Moquette 0.17 支持 MQTT 3.1.1，不支持 MQTT 5.0；
 * 网关设备须按 3.1.1 实现，若厂商仅支持 5.0 触发架构 2.11 外置 Broker 切换评估。</p>
 *
 * <p>端口/凭据支持环境变量：MQTT_PORT（默认 1883）、MQTT_USERNAME / MQTT_PASSWORD。</p>
 */
public class MqttBrokerStarter {

    private static final Logger log = LoggerFactory.getLogger(MqttBrokerStarter.class);

    /** 默认端口（环境变量 MQTT_PORT 可覆盖） */
    public static final int DEFAULT_PORT = Integer.getInteger("MQTT_PORT", 1883);

    private final int port;
    private final String username;
    private final String password;

    /** 收到发布消息计数（T3 验证/自监控） */
    private final AtomicLong receivedPublish = new AtomicLong();

    private Server server;

    public MqttBrokerStarter() {
        this(DEFAULT_PORT,
                System.getenv().getOrDefault("MQTT_USERNAME", "tgaws"),
                System.getenv().getOrDefault("MQTT_PASSWORD", "TgawsMqtt@2026"));
    }

    public MqttBrokerStarter(int port, String username, String password) {
        this.port = port;
        this.username = username;
        this.password = password;
    }

    /** 启动（绑定失败抛 IOException，由装配层决策重试） */
    public void start() throws IOException {
        MqttAuthenticator.configure(username, password);
        Properties props = new Properties();
        props.setProperty(BrokerConstants.PORT_PROPERTY_NAME, String.valueOf(port));
        props.setProperty(BrokerConstants.ALLOW_ANONYMOUS_PROPERTY_NAME, "false");
        props.setProperty(BrokerConstants.AUTHENTICATOR_CLASS_NAME, MqttAuthenticator.class.getName());
        // 数据管道由应用自持（MySQL），Broker 不启用会话持久化；
        // 数据目录按端口隔离（防多实例/测试并发文件锁）
        props.setProperty(BrokerConstants.PERSISTENCE_ENABLED_PROPERTY_NAME, "false");
        props.setProperty(BrokerConstants.DATA_PATH_PROPERTY_NAME,
                System.getProperty("java.io.tmpdir") + "/tgaws-moquette-" + port);
        IConfig config = new MemoryConfig(props);
        server = new Server();
        // 拦截器：仅关注发布计数，其余事件空实现（接口 10 方法全量显式覆盖）
        server.startServer(config, List.of(new InterceptHandler() {
            @Override
            public String getID() {
                return "tgaws-mqtt-publish-counter";
            }

            @Override
            public Class<?>[] getInterceptedMessageTypes() {
                return InterceptHandler.ALL_MESSAGE_TYPES;
            }

            @Override
            public void onConnect(InterceptConnectMessage msg) {
            }

            @Override
            public void onDisconnect(InterceptDisconnectMessage msg) {
            }

            @Override
            public void onConnectionLost(InterceptConnectionLostMessage msg) {
            }

            @Override
            public void onPublish(InterceptPublishMessage msg) {
                receivedPublish.incrementAndGet();
                log.debug("MQTT 发布：topic={}", msg.getTopicName());
            }

            @Override
            public void onSubscribe(InterceptSubscribeMessage msg) {
            }

            @Override
            public void onUnsubscribe(InterceptUnsubscribeMessage msg) {
            }

            @Override
            public void onMessageAcknowledged(InterceptAcknowledgedMessage msg) {
            }

            @Override
            public void onSessionLoopError(Throwable error) {
                log.error("MQTT 会话循环异常", error);
            }
        }));
        log.info("内嵌 MQTT Broker 已启动：端口 {}（MQTT 3.1.1，认证已启用）", port);
    }

    /** 停止 */
    public void stop() {
        if (server != null) {
            server.stopServer();
        }
        log.info("内嵌 MQTT Broker 已停止");
    }

    /** 收到发布消息计数（自监控） */
    public long receivedPublishCount() {
        return receivedPublish.get();
    }
}
