package com.tgaws.access.tcp.protocol;

import java.util.Arrays;

/**
 * TCP 采集协议帧类型（《4.接口设计说明书》4.4.1，TYPE 字段完整使用，协议版本在注册帧协商）。
 */
public enum FrameType {

    REALTIME(0x01, "实时数据"),
    HEARTBEAT(0x02, "心跳"),
    RETRANS(0x03, "补传数据"),
    REGISTER(0x04, "注册请求"),
    ACK(0x10, "ACK 应答");

    private final int code;
    private final String label;

    FrameType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** 按字节解析，未知类型返回 null */
    public static FrameType of(int code) {
        return Arrays.stream(values())
                .filter(t -> t.code == code)
                .findFirst()
                .orElse(null);
    }
}
