package com.tgaws.access.tcp;

import com.tgaws.access.tcp.codec.Crc16Modbus;
import com.tgaws.access.tcp.protocol.FrameType;
import com.tgaws.access.tcp.protocol.TcpFrame;
import com.tgaws.common.pipeline.DataFrame;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TCP 服务端到端验证：真实 Socket 发送协议帧 → 服务端解码 → 统一数据帧入管道。
 */
class TcpServerTest {

    /** 收集管道（内存实现，验证发布链路） */
    private final List<DataFrame> received = new CopyOnWriteArrayList<>();
    private TcpServer server;

    @BeforeEach
    void start() throws Exception {
        server = new TcpServer(0, received::add, 4);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void realSocketRoundTripPublishesDataFrame() throws Exception {
        int port = server.boundPort();
        assertTrue(port > 0);
        byte[] frame = buildRealtimeFrame("GW001", "P00010001", 0, 123456L, 1790301600000L);
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.getOutputStream().write(frame);
            socket.getOutputStream().flush();
        }
        // 等待异步链路
        long deadline = System.currentTimeMillis() + 5000;
        while (received.isEmpty() && System.currentTimeMillis() < deadline) {
            TimeUnit.MILLISECONDS.sleep(50);
        }
        assertEquals(1, received.size(), "应发布 1 条统一数据帧");
        DataFrame df = received.get(0);
        assertNotNull(df);
        assertEquals("GW001", df.gatewayCode());
        assertEquals("P00010001", df.pointCode());
        assertEquals(1790301600000L, df.ts());
        assertEquals(0, new BigDecimal("12.3456").compareTo(df.value()), "缩放换算 123456×10^-4");
        assertEquals(0, df.quality());
        assertEquals(1, df.source());
    }

    /** 与 4.4.1 帧结构一致的构造器（复用 CRC 工具） */
    private static byte[] buildRealtimeFrame(String gwId, String pointCode, int quality,
                                             long valueRaw, long tsMs) {
        List<TcpFrame.FrameItem> items = List.of(new TcpFrame.FrameItem(pointCode, quality, valueRaw));
        int headerFixed = 1 + 8 + 8 + 2;
        int itemsLen = 1 + pointCode.length() + 1 + 8;
        int len = headerFixed + itemsLen;
        ByteBuf buf = Unpooled.buffer(len + 6);
        buf.writeByte(0xAA);
        buf.writeByte(0x55);
        buf.writeShort(len);
        buf.writeByte(FrameType.REALTIME.getCode());
        byte[] gw = gwId.getBytes(StandardCharsets.US_ASCII);
        buf.writeBytes(gw, 0, Math.min(gw.length, 8));
        for (int i = gw.length; i < 8; i++) {
            buf.writeByte(0);
        }
        buf.writeLong(tsMs);
        buf.writeShort(1);
        buf.writeByte(pointCode.length());
        buf.writeBytes(pointCode.getBytes(StandardCharsets.US_ASCII));
        buf.writeByte(quality);
        buf.writeLong(valueRaw);
        byte[] payload = new byte[len];
        buf.getBytes(4, payload);
        int crc = Crc16Modbus.compute(payload);
        buf.writeByte(crc & 0xFF);
        buf.writeByte((crc >>> 8) & 0xFF);
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        return out;
    }
}
