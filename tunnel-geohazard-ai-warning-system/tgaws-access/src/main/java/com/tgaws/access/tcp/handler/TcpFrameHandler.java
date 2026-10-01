package com.tgaws.access.tcp.handler;

import com.tgaws.access.tcp.auth.GatewayAuthService;
import com.tgaws.access.tcp.auth.GatewayOnlineRegistry;
import com.tgaws.access.tcp.protocol.TcpFrame;
import com.tgaws.common.pipeline.DataFrame;
import com.tgaws.common.pipeline.IDataPipeline;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TCP 帧业务处理：限速 → 分派（注册认证/心跳在线/数据帧入管道）。
 *
 * <p>限速：单连接 100 帧/秒，超限断开（《4》4.4.1 行为约定）。</p>
 */
public class TcpFrameHandler extends SimpleChannelInboundHandler<TcpFrame> {

    private static final Logger log = LoggerFactory.getLogger(TcpFrameHandler.class);

    /** 单连接限速（帧/秒） */
    private static final int MAX_FRAMES_PER_SECOND = 100;

    private final IDataPipeline pipeline;

    /** 注册认证（W4-2；null 时注册帧仅告警） */
    private final GatewayAuthService authService;

    /** 在线注册表（心跳维护） */
    private final GatewayOnlineRegistry onlineRegistry;

    /** 协议值缩放系数（实际值 = 整数 × 10^-scale，默认 4） */
    private final int valueScale;

    /** 限速滑动窗口 */
    private final AtomicLong windowStartMs = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong windowFrames = new AtomicLong();

    public TcpFrameHandler(IDataPipeline pipeline, int valueScale,
                           GatewayAuthService authService, GatewayOnlineRegistry onlineRegistry) {
        this.pipeline = pipeline;
        this.valueScale = valueScale;
        this.authService = authService;
        this.onlineRegistry = onlineRegistry;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TcpFrame frame) {
        if (!checkRateLimit(ctx)) {
            return;
        }
        switch (frame.type()) {
            case REALTIME, RETRANS -> publishSamples(frame);
            case HEARTBEAT -> {
                if (onlineRegistry != null) {
                    onlineRegistry.markOnline(frame.gatewayId(), System.currentTimeMillis());
                }
            }
            case REGISTER -> {
                if (authService != null) {
                    authService.handleRegister(ctx, frame);
                } else {
                    log.warn("注册帧到达但认证服务未装配（网关 {}）", frame.gatewayId());
                }
            }
            case ACK -> log.debug("收到 ACK（网关侧不应主动发 ACK）");
        }
    }

    private void publishSamples(TcpFrame frame) {
        if (pipeline == null) {
            log.debug("未装配数据管道，丢弃数据帧（网关 {}，{} 条）", frame.gatewayId(), frame.items().size());
            return;
        }
        for (TcpFrame.FrameItem item : frame.items()) {
            DataFrame dataFrame = new DataFrame(
                    frame.gatewayId(),
                    item.pointCode(),
                    frame.tsMs(),
                    BigDecimal.valueOf(item.valueRaw(), valueScale),
                    item.quality(),
                    DataFrameSource.TCP);
            pipeline.publish(dataFrame);
        }
    }

    private boolean checkRateLimit(ChannelHandlerContext ctx) {
        long now = System.currentTimeMillis();
        long start = windowStartMs.get();
        if (now - start >= 1000L) {
            // 窗口滚动（并发下仅一个线程成功重置）
            windowStartMs.compareAndSet(start, now);
            windowFrames.set(0);
        }
        long count = windowFrames.incrementAndGet();
        if (count > MAX_FRAMES_PER_SECOND) {
            log.warn("连接超限（>{}帧/s），断开 {}", MAX_FRAMES_PER_SECOND, ctx.channel().remoteAddress());
            ctx.close();
            return false;
        }
        return true;
    }

    /** 数据来源（字典 data_source）：TCP=1 */
    private static final class DataFrameSource {
        private static final int TCP = 1;
    }
}
