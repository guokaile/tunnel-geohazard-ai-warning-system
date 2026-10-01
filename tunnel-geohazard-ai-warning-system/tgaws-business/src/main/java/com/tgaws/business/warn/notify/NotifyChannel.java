package com.tgaws.business.warn.notify;

import com.tgaws.common.enums.NotifyChannelType;

/**
 * 通知通道抽象（声光/短信/站内；二期电话语音接入同一契约）。
 */
public interface NotifyChannel {

    NotifyChannelType type();

    String name();

    /**
     * 发送通知。
     *
     * @param target  接收对象（人/报警器编号）
     * @param content 通知内容
     * @throws RuntimeException 发送失败（由 Outbox 补偿任务重试）
     */
    void send(String target, String content);
}
