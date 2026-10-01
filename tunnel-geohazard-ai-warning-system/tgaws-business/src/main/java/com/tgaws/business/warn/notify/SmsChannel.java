package com.tgaws.business.warn.notify;

import com.tgaws.common.enums.NotifyChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 短信通道（供应商占位：sms.enabled=true 且 AK/SK 配置后才启用；
 * 供应商 HTTP 适配器在供应商确定后实现，Outbox 重试机制已就位）。
 */
@Component
@ConditionalOnProperty(name = "sms.enabled", havingValue = "true")
public class SmsChannel implements NotifyChannel {

    private static final Logger log = LoggerFactory.getLogger(SmsChannel.class);

    @Override
    public NotifyChannelType type() {
        return NotifyChannelType.SMS;
    }

    @Override
    public String name() {
        return "短信";
    }

    @Override
    public void send(String target, String content) {
        log.warn("短信通道占位：供应商确定后实现 HTTP 适配器（target={}）", target);
        throw new UnsupportedOperationException("短信供应商未配置（占位通道）");
    }
}
