package com.tgaws.access.tcp.codec;

import com.tgaws.access.tcp.protocol.TcpFrame;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.nio.charset.StandardCharsets;

/**
 * TCP 采集协议帧编码器（服务端→网关，ACK 帧等；与 4.4.1 帧结构严格一致）。
 *
 * <p>注意：GW_ID 定长 8 字节（ASCII 右补 0x00，超长截断）；CRC 低字节先发。</p>
 */
public class TcpFrameEncoder extends MessageToByteEncoder<TcpFrame> {

    @Override
    protected void encode(ChannelHandlerContext ctx, TcpFrame frame, ByteBuf out) {
        int headerFixed = 1 + 8 + 8 + 2; // TYPE + GW_ID + TS + N
        int itemsLen = frame.items().stream()
                .mapToInt(i -> 1 + i.pointCode().length() + 1 + 8)
                .sum();
        int len = headerFixed + itemsLen;
        out.writeByte(TcpFrameCodec.SOF_HIGH);
        out.writeByte(TcpFrameCodec.SOF_LOW);
        out.writeShort(len);
        out.writeByte(frame.type().getCode());
        // GW_ID：8 字节 ASCII 右补 0x00
        byte[] gw = frame.gatewayId().getBytes(StandardCharsets.US_ASCII);
        out.writeBytes(gw, 0, Math.min(gw.length, 8));
        for (int i = gw.length; i < 8; i++) {
            out.writeByte(0);
        }
        out.writeLong(frame.tsMs());
        out.writeShort(frame.items().size());
        for (TcpFrame.FrameItem item : frame.items()) {
            out.writeByte(item.pointCode().length());
            out.writeBytes(item.pointCode().getBytes(StandardCharsets.US_ASCII));
            out.writeByte(item.quality());
            out.writeLong(item.valueRaw());
        }
        // CRC16（TYPE..ITEMS，低字节先发）
        byte[] payload = new byte[len];
        out.getBytes(out.writerIndex() - len, payload);
        int crc = Crc16Modbus.compute(payload);
        out.writeByte(crc & 0xFF);
        out.writeByte((crc >>> 8) & 0xFF);
    }
}
