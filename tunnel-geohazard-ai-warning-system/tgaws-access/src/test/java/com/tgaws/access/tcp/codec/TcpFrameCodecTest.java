package com.tgaws.access.tcp.codec;

import com.tgaws.access.tcp.protocol.FrameType;
import com.tgaws.access.tcp.protocol.TcpFrame;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TCP 帧编解码单测（《4》附录A 逐字节示例帧 + 5 边界用例，联调必测项）。
 */
class TcpFrameCodecTest {

    /** 附录A 完整 44 字节示例帧（LEN=0x0026、CRC16=0x7D53 低字节先发） */
    private static final String DOC_EXAMPLE_FRAME_HEX =
            "AA55002601" + "4757303031000000" + "000001A0D64A7900" + "0001"
                    + "09" + "503030303130303031" + "00" + "000000000001E240" + "537D";

    @Test
    void decodeDocExampleFrameExact() {
        EmbeddedChannel ch = new EmbeddedChannel(new TcpFrameCodec());
        ch.writeInbound(Unpooled.wrappedBuffer(hexToBytes(DOC_EXAMPLE_FRAME_HEX)));

        TcpFrame frame = ch.readInbound();
        assertNotNull(frame);
        assertEquals(FrameType.REALTIME, frame.type());
        assertEquals("GW001", frame.gatewayId());
        assertEquals(1790301600000L, frame.tsMs());
        assertEquals(1, frame.items().size());
        TcpFrame.FrameItem item = frame.items().get(0);
        assertEquals("P00010001", item.pointCode());
        assertEquals(0, item.quality());
        assertEquals(123456L, item.valueRaw());
        assertNull(ch.readInbound(), "不应有残留输出");
        ch.finish();
    }

    @Test
    void halfPacketWaitsForFullFrame() {
        EmbeddedChannel ch = new EmbeddedChannel(new TcpFrameCodec());
        byte[] frame = hexToBytes(DOC_EXAMPLE_FRAME_HEX);
        // 先发 10 字节 → 无输出（半包等待）
        ch.writeInbound(Unpooled.wrappedBuffer(frame, 0, 10));
        assertNull(ch.readInbound());
        // 补齐剩余 → 完整 1 帧
        ch.writeInbound(Unpooled.wrappedBuffer(frame, 10, frame.length - 10));
        assertNotNull(ch.readInbound());
        ch.finish();
    }

    @Test
    void stickyPacketsDecodeAll() {
        EmbeddedChannel ch = new EmbeddedChannel(new TcpFrameCodec());
        byte[] heartbeat = buildFrame(FrameType.HEARTBEAT, "GW001", 1790301600000L, List.of());
        ByteBuf buf = Unpooled.buffer();
        for (int i = 0; i < 3; i++) {
            buf.writeBytes(heartbeat);
        }
        ch.writeInbound(buf);
        for (int i = 0; i < 3; i++) {
            TcpFrame f = ch.readInbound();
            assertNotNull(f, "粘包第 " + i + " 帧应解析");
            assertEquals(FrameType.HEARTBEAT, f.type());
        }
        assertNull(ch.readInbound());
        ch.finish();
    }

    @Test
    void crcCorruptedFrameDroppedThenGoodFrameParsed() {
        EmbeddedChannel ch = new EmbeddedChannel(new TcpFrameCodec());
        byte[] good = hexToBytes(DOC_EXAMPLE_FRAME_HEX);
        byte[] bad = good.clone();
        bad[bad.length - 1] ^= 0x01; // 破坏 CRC
        // 错帧 + 正确帧（模拟粘包）
        ByteBuf buf = Unpooled.buffer();
        buf.writeBytes(bad);
        buf.writeBytes(good);
        ch.writeInbound(buf);
        TcpFrame f = ch.readInbound();
        assertNotNull(f, "错帧后紧跟的正确帧应正常解析");
        assertEquals("GW001", f.gatewayId());
        assertEquals(1, ((TcpFrameCodec) ch.pipeline().get(TcpFrameCodec.class)).badFrameCount());
        ch.finish();
    }

    @Test
    void heartbeatFrameZeroItems() {
        EmbeddedChannel ch = new EmbeddedChannel(new TcpFrameCodec());
        byte[] heartbeat = buildFrame(FrameType.HEARTBEAT, "GW002", 1790301600000L, List.of());
        ch.writeInbound(Unpooled.wrappedBuffer(heartbeat));
        TcpFrame f = ch.readInbound();
        assertNotNull(f);
        assertEquals(FrameType.HEARTBEAT, f.type());
        assertTrue(f.items().isEmpty());
        assertEquals("GW002", f.gatewayId());
        ch.finish();
    }

    @Test
    void oversizedFrameDroppedAndCounted() {
        EmbeddedChannel ch = new EmbeddedChannel(new TcpFrameCodec());
        // N=2000 超过上限 1024
        byte[] frame = buildFrame(FrameType.REALTIME, "GW001", 1790301600000L, 2000, List.of());
        ch.writeInbound(Unpooled.wrappedBuffer(frame));
        assertNull(ch.readInbound(), "超长帧应被丢弃");
        assertEquals(1, ((TcpFrameCodec) ch.pipeline().get(TcpFrameCodec.class)).badFrameCount());
        ch.finish();
    }

    // ---------- 测试辅助 ----------

    /** 构造合法帧（含 LEN 与 CRC） */
    private static byte[] buildFrame(FrameType type, String gwId, long tsMs, List<TcpFrame.FrameItem> items) {
        return buildFrame(type, gwId, tsMs, items.size(), items);
    }

    /** 构造帧（N 与实际 items 可分离，用于超长帧用例） */
    private static byte[] buildFrame(FrameType type, String gwId, long tsMs, int declaredN,
                                     List<TcpFrame.FrameItem> items) {
        int headerFixed = 1 + 8 + 8 + 2;
        int itemsLen = items.stream().mapToInt(i -> 1 + i.pointCode().length() + 1 + 8).sum();
        int len = headerFixed + itemsLen;
        ByteBuf buf = Unpooled.buffer(len + 6);
        buf.writeByte(0xAA);
        buf.writeByte(0x55);
        buf.writeShort(len);
        buf.writeByte(type.getCode());
        byte[] gw = gwId.getBytes(StandardCharsets.US_ASCII);
        buf.writeBytes(gw, 0, Math.min(gw.length, 8));
        for (int i = gw.length; i < 8; i++) {
            buf.writeByte(0);
        }
        buf.writeLong(tsMs);
        buf.writeShort(declaredN);
        for (TcpFrame.FrameItem item : items) {
            buf.writeByte(item.pointCode().length());
            buf.writeBytes(item.pointCode().getBytes(StandardCharsets.US_ASCII));
            buf.writeByte(item.quality());
            buf.writeLong(item.valueRaw());
        }
        byte[] payload = new byte[len];
        buf.getBytes(4, payload);
        int crc = Crc16Modbus.compute(payload);
        buf.writeByte(crc & 0xFF);
        buf.writeByte((crc >>> 8) & 0xFF);
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        return out;
    }

    private static byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    /** 供其余用例构造数据项 */
    static List<TcpFrame.FrameItem> itemList(String code, int quality, long valueRaw) {
        List<TcpFrame.FrameItem> list = new ArrayList<>();
        list.add(new TcpFrame.FrameItem(code, quality, valueRaw));
        return list;
    }
}
