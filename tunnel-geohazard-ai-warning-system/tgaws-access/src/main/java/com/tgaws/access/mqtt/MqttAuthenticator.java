package com.tgaws.access.mqtt;

import io.moquette.broker.security.IAuthenticator;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * MQTT 连接认证器（Moquette 以类名反射实例化，须公开无参构造）。
 *
 * <p>单机内嵌 Broker 场景，凭据由 {@link MqttBrokerStarter} 启动前配置
 * （单实例进程，静态持有可接受；对应 `${MQTT_USERNAME}`/`${MQTT_PASSWORD}`）。</p>
 */
public class MqttAuthenticator implements IAuthenticator {

    private static volatile String expectUsername;
    private static volatile byte[] expectPassword;

    /** 启动前配置期望凭据（MqttBrokerStarter 调用） */
    public static void configure(String username, String password) {
        expectUsername = username;
        expectPassword = password == null ? null : password.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean checkValid(String clientId, String username, byte[] password) {
        if (expectUsername == null) {
            return false;
        }
        return expectUsername.equals(username)
                && Arrays.equals(expectPassword, password == null ? new byte[0] : password);
    }
}
