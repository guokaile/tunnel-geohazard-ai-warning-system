package com.tgaws.access.tcp;

import com.tgaws.access.tcp.auth.GatewayAuthService;
import com.tgaws.access.tcp.auth.GatewayOnlineRegistry;
import com.tgaws.common.gateway.IGatewaySecretProvider;
import com.tgaws.access.tcp.codec.TcpFrameCodec;
import com.tgaws.access.tcp.codec.TcpFrameEncoder;
import com.tgaws.access.tcp.handler.TcpFrameHandler;
import com.tgaws.common.pipeline.IDataPipeline;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * TCP 采集接入服务（《4.接口设计说明书》4.4.1，默认端口 9000）。
 *
 * <p>端口与缩放系数支持环境变量覆盖：TCP_GATEWAY_PORT / PROTOCOL_VALUE_SCALE；
 * 网关注册认证与在线维护由 {@link GatewayAuthService}/{@link GatewayOnlineRegistry} 承担。</p>
 */
public class TcpServer {

    private static final Logger log = LoggerFactory.getLogger(TcpServer.class);

    /** 默认端口（环境变量 TCP_GATEWAY_PORT 可覆盖） */
    public static final int DEFAULT_PORT = Integer.getInteger("TCP_GATEWAY_PORT", 9000);

    private final int port;
    private final int valueScale;
    private final IDataPipeline pipeline;
    /** 服务级坏帧计数器（每通道独立编解码实例——ByteToMessageDecoder 不可 @Sharable，计数聚合） */
    private final java.util.concurrent.atomic.AtomicLong badFrameCounter =
            new java.util.concurrent.atomic.AtomicLong();
    private final GatewayOnlineRegistry onlineRegistry;
    private final GatewayAuthService authService;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public TcpServer(IDataPipeline pipeline) {
        this(DEFAULT_PORT, pipeline, null, Integer.getInteger("PROTOCOL_VALUE_SCALE", 4));
    }

    public TcpServer(int port, IDataPipeline pipeline, int valueScale) {
        this(port, pipeline, null, valueScale);
    }

    public TcpServer(int port, IDataPipeline pipeline, IGatewaySecretProvider secretProvider, int valueScale) {
        this(port, pipeline, secretProvider, valueScale, null);
    }

    /**
     * @param onlineRegistry 在线注册表（生产装配必须注入共享 Bean——心跳数据须进入
     *                       离线扫描器视图；null 时自建实例，仅测试用）
     */
    public TcpServer(int port, IDataPipeline pipeline, IGatewaySecretProvider secretProvider,
                     int valueScale, GatewayOnlineRegistry onlineRegistry) {
        this.port = port;
        this.pipeline = pipeline;
        this.valueScale = valueScale;
        this.onlineRegistry = onlineRegistry == null ? new GatewayOnlineRegistry() : onlineRegistry;
        // 注册防重放窗口（秒，环境变量可调；生产由 sys_config 覆盖注入）
        long registerWindowMs = Integer.getInteger("PROTOCOL_REGISTER_WINDOW_SEC", 300) * 1000L;
        this.authService = secretProvider == null
                ? null
                : new GatewayAuthService(secretProvider, this.onlineRegistry, valueScale, registerWindowMs);
    }

    /** 启动（绑定失败抛异常，由装配层决策重试） */
    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast("frameCodec", new TcpFrameCodec(badFrameCounter))
                                .addLast("frameEncoder", new TcpFrameEncoder())
                                .addLast("frameHandler",
                                        new TcpFrameHandler(pipeline, valueScale, authService, onlineRegistry));
                    }
                });
        serverChannel = bootstrap.bind(new InetSocketAddress(port)).sync().channel();
        log.info("TCP 采集服务已启动：端口 {}（scale={}，注册认证={}）", port, valueScale, authService != null);
    }

    /** 停止（优雅关停：先关 accept 再关 IO 线程） */
    public void stop() {
        if (serverChannel != null) {
            serverChannel.close().awaitUninterruptibly();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().awaitUninterruptibly();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully().awaitUninterruptibly();
        }
        log.info("TCP 采集服务已停止");
    }

    /** 错帧计数（自监控 D0006 联动） */
    public long badFrameCount() {
        return badFrameCounter.get();
    }

    /** 网关在线注册表（心跳/离线判定） */
    public GatewayOnlineRegistry onlineRegistry() {
        return onlineRegistry;
    }

    /** 实际绑定端口（port=0 时用于测试） */
    public int boundPort() {
        return serverChannel == null ? -1
                : ((InetSocketAddress) serverChannel.localAddress()).getPort();
    }
}
