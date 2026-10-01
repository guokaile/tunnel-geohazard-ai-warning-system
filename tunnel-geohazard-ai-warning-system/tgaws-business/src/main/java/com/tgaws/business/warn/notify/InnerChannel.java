package com.tgaws.business.warn.notify;

import com.tgaws.common.enums.NotifyChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 站内消息通道（消息本体即 warn_notify_log 记录：SSE 推送 + 日志列表查询展示）。
 */
@Component
public class InnerChannel implements NotifyChannel {

    private static final Logger log = LoggerFactory.getLogger(InnerChannel.class);

    @Override
    public NotifyChannelType type() {
        return NotifyChannelType.INNER;
    }

    @Override
    public String name() {
        return "站内消息";
    }

    @Override
    public void send(String target, String content) {
        log.info("站内消息：{} → {}", target, content);
        // 记录行即消息；SSE 推送由 WarnEventPublisher（W6-605）广播
    }
}
