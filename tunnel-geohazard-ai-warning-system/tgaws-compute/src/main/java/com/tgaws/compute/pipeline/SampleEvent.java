package com.tgaws.compute.pipeline;

import java.math.BigDecimal;

/**
 * 管道事件（RingBuffer 复用对象，字段全量覆盖写）。
 */
public class SampleEvent {

    private String gatewayCode;
    private String pointCode;
    private long tsMs;
    private BigDecimal value;
    private int quality;
    private int source;

    public void set(String gatewayCode, String pointCode, long tsMs,
                    BigDecimal value, int quality, int source) {
        this.gatewayCode = gatewayCode;
        this.pointCode = pointCode;
        this.tsMs = tsMs;
        this.value = value;
        this.quality = quality;
        this.source = source;
    }

    public String gatewayCode() {
        return gatewayCode;
    }

    public String pointCode() {
        return pointCode;
    }

    public long tsMs() {
        return tsMs;
    }

    public BigDecimal value() {
        return value;
    }

    public int quality() {
        return quality;
    }

    public int source() {
        return source;
    }
}
