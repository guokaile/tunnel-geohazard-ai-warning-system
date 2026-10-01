package com.tgaws.business.warn.notify;

import com.tgaws.common.enums.NotifyChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 声光报警通道（占位：light-alarm.enabled=true 且现场报警器地址配置后启用；
 * HTTP 适配器（Bearer+IP 白名单，评审 2.13 已定稿）在设备到场后联调实现）。
 */
@Component
@ConditionalOnProperty(name = "light-alarm.enabled", havingValue = "true")
public class LightAlarmChannel implements NotifyChannel {

    private static final Logger log = LoggerFactory.getLogger(LightAlarmChannel.class);

    @Override
    public NotifyChannelType type() {
        return NotifyChannelType.LIGHT_ALARM;
    }

    @Override
    public String name() {
        return "声光报警";
    }

    @Override
    public void send(String target, String content) {
        log.warn("声光通道占位：现场设备联调后实现（deviceCode={}）", target);
        throw new UnsupportedOperationException("声光设备未配置（占位通道）");
    }
}
