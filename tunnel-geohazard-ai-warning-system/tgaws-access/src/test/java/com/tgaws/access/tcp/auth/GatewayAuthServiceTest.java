package com.tgaws.access.tcp.auth;

import com.tgaws.access.tcp.codec.TcpFrameCodec;
import com.tgaws.access.tcp.codec.TcpFrameEncoder;
import com.tgaws.access.tcp.handler.TcpFrameHandler;
import com.tgaws.access.tcp.protocol.FrameType;
import com.tgaws.access.tcp.protocol.TcpFrame;
import com.tgaws.common.gateway.GatewayCredential;
import com.tgaws.common.gateway.IGatewaySecretProvider;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网关注册认证单测（《4》4.4.1 注册流程 + ACK 结果码 0/2/4/5）。
 */
class GatewayAuthServiceTest {

    private static final String PSK = "test-psk";
    private static final String GW = "GW001";
    private static final int SCALE = 4;

    private GatewayOnlineRegistry registry;
    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        registry = new GatewayOnlineRegistry();
        IGatewaySecretProvider provider = code -> GW.equals(code)
                ? new GatewayCredential(PSK, true) : null;
        GatewayAuthService auth = new GatewayAuthService(provider, registry, SCALE);
        channel = new EmbeddedChannel(
                new TcpFrameCodec(),
                new TcpFrameEncoder(),
                new TcpFrameHandler(null, SCALE, auth, registry));
    }

    @Test
    void registerSuccessAcksResult0AndKeepsOnline() {
        long ts = System.currentTimeMillis();
        String digest = HmacUtil.hmacSha256Hex(PSK, GW + ts).substring(0, 32);
        TcpFrame register = new TcpFrame(FrameType.REGISTER, GW, ts,
                List.of(new TcpFrame.FrameItem(digest, 0, 10000L))); // 版本 1×10^4

        // 直接以对象入站：EmbeddedChannel 绕过 codec，从 handler 位置写入
        channel.pipeline().context(TcpFrameCodec.class).fireChannelRead(register);
        // 出站 ACK 由 encoder 编码为 ByteBuf
        io.netty.buffer.ByteBuf ackBuf = channel.readOutbound();
        assertNotNull(ackBuf, "应回 ACK 帧");
        // 解码 ACK 校验结果码
        EmbeddedChannel decodeCh = new EmbeddedChannel(new TcpFrameCodec());
        decodeCh.writeInbound(ackBuf);
        TcpFrame ack = decodeCh.readInbound();
        assertNotNull(ack);
        assertEquals(FrameType.ACK, ack.type());
        assertEquals(0, ack.items().get(0).quality(), "结果码应为 0（成功）");
        assertEquals("RSP", ack.items().get(0).pointCode());
        assertTrue(channel.isActive(), "注册成功不应断开连接");
        assertEquals(System.currentTimeMillis(), registry.lastSeenOf(GW), 5000L, "注册成功应刷新在线时间");
    }

    @Test
    void unknownGatewayAcksResult2() {
        TcpFrame register = new TcpFrame(FrameType.REGISTER, "GW999", System.currentTimeMillis(),
                List.of(new TcpFrame.FrameItem("x", 0, 10000L)));
        channel.pipeline().context(TcpFrameCodec.class).fireChannelRead(register);
        TcpFrame ack = decodeAck();
        assertNotNull(ack);
        assertEquals(2, ack.items().get(0).quality(), "未注册 → 结果码 2");
    }

    @Test
    void badCredentialAcksResult4() {
        TcpFrame register = new TcpFrame(FrameType.REGISTER, GW, System.currentTimeMillis(),
                List.of(new TcpFrame.FrameItem("wrong-digest", 0, 10000L)));
        channel.pipeline().context(TcpFrameCodec.class).fireChannelRead(register);
        TcpFrame ack = decodeAck();
        assertNotNull(ack);
        assertEquals(4, ack.items().get(0).quality(), "凭据错误 → 结果码 4");
    }

    @Test
    void unsupportedVersionAcksResult5() {
        long ts = System.currentTimeMillis();
        String digest = HmacUtil.hmacSha256Hex(PSK, GW + ts).substring(0, 32);
        TcpFrame register = new TcpFrame(FrameType.REGISTER, GW, ts,
                List.of(new TcpFrame.FrameItem(digest, 0, 20000L))); // 版本 2
        channel.pipeline().context(TcpFrameCodec.class).fireChannelRead(register);
        TcpFrame ack = decodeAck();
        assertNotNull(ack);
        assertEquals(5, ack.items().get(0).quality(), "版本不支持 → 结果码 5");
    }

    @Test
    void staleTimestampRejectedWithResult6() {
        // 时间戳超窗（-10min，疑似重放）→ 结果码 6
        long staleTs = System.currentTimeMillis() - 600_000L;
        String digest = HmacUtil.hmacSha256Hex(PSK, GW + staleTs).substring(0, 32);
        TcpFrame register = new TcpFrame(FrameType.REGISTER, GW, staleTs,
                List.of(new TcpFrame.FrameItem(digest, 0, 10000L)));
        channel.pipeline().context(TcpFrameCodec.class).fireChannelRead(register);
        TcpFrame ack = decodeAck();
        assertNotNull(ack);
        assertEquals(6, ack.items().get(0).quality(), "时间戳超窗 → 结果码 6");
    }

    @Test
    void secondsTimestampRejectedAsUnitMismatchGuard() {
        // TS 单位为毫秒：若网关按"秒"实现（如 1790301600），将被时间窗拦截——
        // 本用例守护契约单位，防止"实现毫秒/文档秒"回归
        long secondsTs = 1790301600L;
        String digest = HmacUtil.hmacSha256Hex(PSK, GW + secondsTs).substring(0, 32);
        TcpFrame register = new TcpFrame(FrameType.REGISTER, GW, secondsTs,
                List.of(new TcpFrame.FrameItem(digest, 0, 10000L)));
        channel.pipeline().context(TcpFrameCodec.class).fireChannelRead(register);
        TcpFrame ack = decodeAck();
        assertNotNull(ack);
        assertEquals(6, ack.items().get(0).quality(), "秒级时间戳必须被时间窗拒绝（单位契约守护）");
    }

    @Test
    void heartbeatRefreshesOnline() {
        registry.clear();
        channel.pipeline().context(TcpFrameCodec.class)
                .fireChannelRead(new TcpFrame(FrameType.HEARTBEAT, GW, System.currentTimeMillis(), List.of()));
        assertEquals(System.currentTimeMillis(), registry.lastSeenOf(GW), 5000L);
        assertFalse(registry.isOffline(GW, System.currentTimeMillis(), 180));
    }

    private TcpFrame decodeAck() {
        io.netty.buffer.ByteBuf ackBuf = channel.readOutbound();
        if (ackBuf == null) {
            return null;
        }
        EmbeddedChannel decodeCh = new EmbeddedChannel(new TcpFrameCodec());
        decodeCh.writeInbound(ackBuf);
        return decodeCh.readInbound();
    }
}
