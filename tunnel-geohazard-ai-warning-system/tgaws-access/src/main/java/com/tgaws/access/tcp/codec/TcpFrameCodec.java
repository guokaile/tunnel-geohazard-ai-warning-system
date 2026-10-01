package com.tgaws.access.tcp.codec;

import com.tgaws.access.tcp.protocol.FrameType;
import com.tgaws.access.tcp.protocol.TcpFrame;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TCP 采集协议帧解码器（《4.接口设计说明书》4.4.1 组帧规则 + 附录A 解析伪代码）。
 *
 * <p>组帧规则（LEN 是帧边界识别的唯一依据）：</p>
 * <ol>
 *   <li>先读满 4 字节（SOF+LEN），SOF≠0xAA55 逐字节滑动重同步；</li>
 *   <li>按 LEN 取满整帧（半包等待补齐）；</li>
 *   <li>CRC16 校验失败则丢弃+计数，继续扫描下一个 0xAA55（防错帧粘包）；</li>
 *   <li>校验通过 → 分派处理。</li>
 * </ol>
 *
 * <p>约束：LEN ∈ [19, 65535]；数据点数 N ≤ 1024；点位编码 ≤32 字节。</p>
 */
public class TcpFrameCodec extends ByteToMessageDecoder {

    private static final Logger log = LoggerFactory.getLogger(TcpFrameCodec.class);

    /** 帧头 */
    static final int SOF_HIGH = 0xAA;
    static final int SOF_LOW = 0x55;

    /** LEN 最小值：TYPE(1) + GW_ID(8) + TS(8) + N(2) = 19 */
    static final int MIN_LEN = 19;
    static final int MAX_LEN = 65535;

    /** 单帧数据点数上限 */
    static final int MAX_ITEM_COUNT = 1024;

    /** 点位编码长度上限 */
    static final int MAX_POINT_CODE_LEN = 32;

    /** 错帧计数（自监控：持续增长即链路异常；支持服务级共享计数器） */
    private final AtomicLong badFrameCount;

    public TcpFrameCodec() {
        this(new AtomicLong());
    }

    /** @param sharedBadCounter 服务级共享计数器（每通道独立解码器实例，计数聚合） */
    public TcpFrameCodec(AtomicLong sharedBadCounter) {
        this.badFrameCount = sharedBadCounter;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        while (true) {
            if (in.readableBytes() < 4) {
                return; // 半包：等待帧头
            }
            // 1) 重同步：SOF 校验
            if ((in.getUnsignedByte(in.readerIndex()) != SOF_HIGH)
                    || (in.getUnsignedByte(in.readerIndex() + 1) != SOF_LOW)) {
                in.readByte();
                badFrameCount.incrementAndGet();
                continue;
            }
            int len = in.getUnsignedShort(in.readerIndex() + 2);
            // 2) LEN 合法性
            if (len < MIN_LEN || len > MAX_LEN) {
                in.readByte();
                badFrameCount.incrementAndGet();
                continue;
            }
            // 3) 半包：等待整帧（4 字节头 + LEN + 2 字节 CRC）
            if (in.readableBytes() < 4 + len + 2) {
                return;
            }
            ByteBuf frameBuf = in.readSlice(4 + len + 2);
            if (!parseFrame(frameBuf, out)) {
                badFrameCount.incrementAndGet();
                // 错帧：已消费本帧，继续扫描（粘包循环）
            }
        }
    }

    /**
     * 解析单帧；CRC 失败返回 false（帧已消费）。
     */
    private boolean parseFrame(ByteBuf buf, List<Object> out) {
        buf.readShort(); // SOF
        int len = buf.readUnsignedShort();
        int typeCode = buf.readUnsignedByte();
        byte[] gwBytes = new byte[8];
        buf.readBytes(gwBytes);
        long tsMs = buf.readLong();
        int n = buf.readUnsignedShort();
        int payloadLen = len; // LEN 覆盖 TYPE 起
        // 数据块变长部分字节数
        int remaining = payloadLen - (1 + 8 + 8 + 2);
        if (n > MAX_ITEM_COUNT) {
            log.warn("超长帧：N={} 超过上限 {}", n, MAX_ITEM_COUNT);
            return false;
        }
        List<TcpFrame.FrameItem> items = new ArrayList<>(Math.min(n, 1024));
        boolean parseOk = true;
        for (int i = 0; i < n; i++) {
            if (remaining < 1) {
                parseOk = false;
                break;
            }
            int pcLen = buf.readUnsignedByte();
            remaining -= 1;
            if (pcLen == 0 || pcLen > MAX_POINT_CODE_LEN || remaining < pcLen + 1 + 8) {
                parseOk = false;
                break;
            }
            byte[] pcBytes = new byte[pcLen];
            buf.readBytes(pcBytes);
            int quality = buf.readUnsignedByte();
            long valueRaw = buf.readLong();
            remaining -= (pcLen + 1 + 8);
            items.add(new TcpFrame.FrameItem(
                    new String(pcBytes, StandardCharsets.US_ASCII), quality, valueRaw));
        }
        if (!parseOk || remaining != 0) {
            log.warn("帧数据块长度不匹配：N={} 剩余={}", n, remaining);
            return false;
        }
        // CRC16（低字节先发）
        byte crcLow = buf.readByte();
        byte crcHigh = buf.readByte();
        // 提取 CRC 覆盖区（TYPE..ITEMS）需要回看：用 getBytes 重建
        byte[] payload = new byte[len];
        buf.getBytes(buf.readerIndex() - 2 - len, payload);
        if (!Crc16Modbus.verify(payload, crcLow, crcHigh)) {
            log.warn("CRC 校验失败，丢弃帧");
            return false;
        }
        FrameType type = FrameType.of(typeCode);
        if (type == null) {
            log.warn("未知帧类型 0x{}", Integer.toHexString(typeCode));
            return false;
        }
        String gatewayId = new String(gwBytes, StandardCharsets.US_ASCII).trim();
        out.add(new TcpFrame(type, gatewayId, tsMs, items));
        return true;
    }

    /** 错帧计数（自监控指标） */
    public long badFrameCount() {
        return badFrameCount.get();
    }
}
