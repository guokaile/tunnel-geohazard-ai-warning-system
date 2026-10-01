package com.tgaws.access.mqtt;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T3 验证：Moquette 0.17 内嵌 Broker——启停/认证/发布订阅全链路 + 协议版本声明。
 *
 * <p>协议版本声明：MQTT 3.1.1（MqttConnectOptions.MQTT_VERSION_3_1_1）；
 * Moquette 0.17 不支持 MQTT 5.0（网关厂商约束见《4》4.4.2）。</p>
 */
class MqttBrokerTest {

    private static final String TOPIC = "tgaws/v1/gateway/GW001/data";

    @Test
    void pubSubRoundTripWithAuth() throws Exception {
        int port = freePort();
        MqttBrokerStarter broker = new MqttBrokerStarter(port, "gwuser", "gwpass");
        broker.start();
        try {
            CountDownLatch received = new CountDownLatch(1);
            MqttClient client = new MqttClient("tcp://127.0.0.1:" + port, "t3-client", new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName("gwuser");
            options.setPassword("gwpass".toCharArray());
            options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1); // 协议版本声明
            options.setConnectionTimeout(5);
            client.connect(options);
            client.subscribe(TOPIC, 1, (topic, msg) -> received.countDown());
            client.publish(TOPIC,
                    new MqttMessage("{\"gw\":\"GW001\",\"ts\":1790301600123,\"items\":[]}"
                            .getBytes(StandardCharsets.UTF_8)));
            assertTrue(received.await(5, TimeUnit.SECONDS), "订阅端应收到发布消息");
            // 服务端拦截计数
            TimeUnit.MILLISECONDS.sleep(200);
            assertEquals(1, broker.receivedPublishCount(), "Broker 应计数 1 条发布");
            client.disconnect();
            client.close();
        } finally {
            broker.stop();
        }
    }

    @Test
    void wrongPasswordRejected() throws Exception {
        int port = freePort();
        MqttBrokerStarter broker = new MqttBrokerStarter(port, "gwuser", "gwpass");
        broker.start();
        try {
            MqttClient client = new MqttClient("tcp://127.0.0.1:" + port, "t3-bad", new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName("gwuser");
            options.setPassword("wrong".toCharArray());
            options.setConnectionTimeout(5);
            assertThrows(MqttException.class, client::connect, "错误凭据应拒绝连接");
            client.close();
        } finally {
            broker.stop();
        }
    }

    @Test
    void anonymousRejected() throws Exception {
        int port = freePort();
        MqttBrokerStarter broker = new MqttBrokerStarter(port, "gwuser", "gwpass");
        broker.start();
        try {
            MqttClient client = new MqttClient("tcp://127.0.0.1:" + port, "t3-anon", new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setConnectionTimeout(5);
            assertThrows(MqttException.class, client::connect, "匿名连接应被拒绝（allow_anonymous=false）");
            client.close();
        } finally {
            broker.stop();
        }
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            int port = socket.getLocalPort();
            assertNotNull(port);
            return port;
        }
    }
}
