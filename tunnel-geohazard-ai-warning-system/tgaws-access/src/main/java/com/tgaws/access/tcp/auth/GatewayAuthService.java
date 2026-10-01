package com.tgaws.access.tcp.auth;

import com.tgaws.access.tcp.protocol.FrameType;
import com.tgaws.access.tcp.protocol.TcpFrame;
import com.tgaws.common.gateway.GatewayCredential;
import com.tgaws.common.gateway.IGatewaySecretProvider;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

/**
 * 网关注册认证（《4.接口设计说明书》4.4.1 注册流程 + ACK 结果码）。
 *
 * <p>0x04 注册帧：N=1，数据块为凭据项——PC 字段填 HMAC-SHA256(PSK, GW_ID+TS) 的 HEX 前 32 字符，
 * Q=0，VALUE=协议版本号×10^scale；校验通过回 ACK（结果码 0 + 服务器时间戳授时），
 * 失败回对应结果码并断开。</p>
 *
 * <p>ACK 结果码：0 成功 / 1 CRC 错误 / 2 网关未注册 / 3 点位未配置 / 4 凭据错误 / 5 版本不支持。</p>
 */
public class GatewayAuthService {

    private static final Logger log = LoggerFactory.getLogger(GatewayAuthService.class);

    /** 支持协议版本 */
    public static final int SUPPORTED_PROTOCOL_VERSION = 1;

    /** 注册帧时间戳新鲜度窗口默认值（±5min，防重放；sys_config protocol.register_window_sec 可调） */
    public static final long DEFAULT_REGISTER_WINDOW_MS = 300_000L;

    /** 凭据摘要截断长度（HMAC HEX 前 32 字符，契合 PC 字段 ≤32 字节上限） */
    private static final int CRED_DIGEST_LEN = 32;

    private final IGatewaySecretProvider secretProvider;
    private final GatewayOnlineRegistry onlineRegistry;
    private final int valueScale;
    private final long registerWindowMs;

    public GatewayAuthService(IGatewaySecretProvider secretProvider,
                              GatewayOnlineRegistry onlineRegistry,
                              int valueScale) {
        this(secretProvider, onlineRegistry, valueScale, DEFAULT_REGISTER_WINDOW_MS);
    }

    public GatewayAuthService(IGatewaySecretProvider secretProvider,
                              GatewayOnlineRegistry onlineRegistry,
                              int valueScale,
                              long registerWindowMs) {
        this.secretProvider = secretProvider;
        this.onlineRegistry = onlineRegistry;
        this.valueScale = valueScale;
        this.registerWindowMs = registerWindowMs;
    }

    /**
     * 处理注册帧：校验 → 授时 ACK（成功保持连接）；失败回结果码并断开。
     */
    public void handleRegister(ChannelHandlerContext ctx, TcpFrame frame) {
        String gwId = frame.gatewayId();
        GatewayCredential cred = secretProvider == null ? null : secretProvider.credentialOf(gwId);
        if (cred == null || !cred.enabled()) {
            log.warn("网关 {} 未注册或已停用，拒绝注册", gwId);
            ack(ctx, frame, 2, true);
            return;
        }
        if (frame.items() == null || frame.items().size() != 1) {
            ack(ctx, frame, 4, true);
            return;
        }
        TcpFrame.FrameItem credItem = frame.items().get(0);
        // 协议版本：VALUE = 版本号 × 10^scale
        int version = BigDecimal.valueOf(credItem.valueRaw(), valueScale).intValueExact();
        if (version != SUPPORTED_PROTOCOL_VERSION) {
            log.warn("网关 {} 协议版本 {} 不支持", gwId, version);
            ack(ctx, frame, 5, true);
            return;
        }
        // 防重放：时间戳新鲜度（±窗口；注册发生在授时之前，窗口可配置放宽）
        long now = System.currentTimeMillis();
        if (Math.abs(now - frame.tsMs()) > registerWindowMs) {
            log.warn("网关 {} 注册时间戳超窗（偏差 {}ms，窗口 {}ms），疑似重放", gwId, now - frame.tsMs(), registerWindowMs);
            ack(ctx, frame, 6, true);
            return;
        }
        String expected = HmacUtil.hmacSha256Hex(cred.secret(), gwId + frame.tsMs())
                .substring(0, CRED_DIGEST_LEN);
        if (!expected.equalsIgnoreCase(credItem.pointCode())) {
            log.warn("网关 {} 凭据校验失败", gwId);
            ack(ctx, frame, 4, true);
            return;
        }
        onlineRegistry.markOnline(gwId, System.currentTimeMillis());
        log.info("网关 {} 注册成功，授时 {}ms", gwId, System.currentTimeMillis());
        ack(ctx, frame, 0, false);
    }

    private void ack(ChannelHandlerContext ctx, TcpFrame registerFrame, int resultCode, boolean close) {
        long serverTs = System.currentTimeMillis();
        TcpFrame ack = new TcpFrame(
                FrameType.ACK,
                registerFrame.gatewayId(),
                serverTs,
                List.of(new TcpFrame.FrameItem("RSP", resultCode, serverTs)));
        ctx.writeAndFlush(ack);
        if (close) {
            ctx.close();
        }
    }
}
