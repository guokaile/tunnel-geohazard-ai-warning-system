package com.tgaws.tools.gateway;

import com.tgaws.access.tcp.auth.HmacUtil;
import com.tgaws.access.tcp.codec.TcpFrameCodec;
import com.tgaws.access.tcp.codec.TcpFrameEncoder;
import com.tgaws.access.tcp.protocol.FrameType;
import com.tgaws.access.tcp.protocol.TcpFrame;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 网关模拟器（工具模块）：注册（HMAC 凭据）→ ACK 校验 → 心跳 → 数据上报全流程。
 *
 * <p>复用 tgaws-access 协议层（编解码唯一权威源）；用途：</p>
 * <ul>
 *   <li>T2 压测（W4-4，1000 点×30s×24h）；</li>
 *   <li>T-902b 与厂商协议文档离线联调；</li>
 *   <li>T-902a 协议确认会材料（现场可交给厂商对照实现）。</li>
 * </ul>
 *
 * <p>用法：java -cp ... GatewaySimulator --host 127.0.0.1 --port 9000 --gw GW001
 * --psk &lt;PSK&gt; --points P00010001,P00010002 --freq-ms 1000 --duration-sec 60 --scale 4</p>
 */
public final class GatewaySimulator {

    private static final Logger log = LoggerFactory.getLogger(GatewaySimulator.class);

    private final String host;
    private final int port;
    private final String gatewayId;
    private final String psk;
    private final List<String> pointCodes;
    private final long freqMs;
    private final long durationSec;
    private final int scale;
    private final long heartbeatSec;

    private EventLoopGroup group;
    private Channel channel;
    private final CountDownLatch ackLatch = new CountDownLatch(1);
    private final AtomicReference<TcpFrame> ackFrame = new AtomicReference<>();
    private final AtomicLong sentFrames = new AtomicLong();
    private final AtomicLong sentItems = new AtomicLong();

    GatewaySimulator(Builder b) {
        this.host = b.host;
        this.port = b.port;
        this.gatewayId = b.gatewayId;
        this.psk = b.psk;
        this.pointCodes = b.pointCodes;
        this.freqMs = b.freqMs;
        this.durationSec = b.durationSec;
        this.scale = b.scale;
        this.heartbeatSec = b.heartbeatSec;
    }

    /**
     * 运行模拟器；返回退出码（0=注册成功且完成发送，2/4/5=注册失败结果码，-1=连接/超时失败）。
     */
    public int run() throws Exception {
        group = new NioEventLoopGroup(1);
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast("frameCodec", new TcpFrameCodec())
                                    .addLast("frameEncoder", new TcpFrameEncoder())
                                    .addLast("inbound", new SimpleChannelInboundHandler<TcpFrame>() {
                                        @Override
                                        protected void channelRead0(io.netty.channel.ChannelHandlerContext ctx,
                                                                    TcpFrame frame) {
                                            if (frame.type() == FrameType.ACK) {
                                                ackFrame.set(frame);
                                                ackLatch.countDown();
                                            }
                                        }
                                    });
                        }
                    });
            ChannelFuture cf = bootstrap.connect(new InetSocketAddress(host, port)).sync();
            channel = cf.channel();

            // 1) 注册
            sendRegister();
            if (!ackLatch.await(5, TimeUnit.SECONDS)) {
                log.error("等待 ACK 超时（5s）");
                return -1;
            }
            TcpFrame ack = ackFrame.get();
            int resultCode = ack.items().isEmpty() ? -1 : ack.items().get(0).quality();
            if (resultCode != 0) {
                log.error("注册失败，结果码 {}", resultCode);
                return resultCode;
            }
            log.info("注册成功：网关 {}（服务器授时 {}ms）", gatewayId, ack.items().get(0).valueRaw());

            // 2) 心跳线程
            Thread heartbeat = new Thread(this::heartbeatLoop, "gw-heartbeat");
            heartbeat.setDaemon(true);
            heartbeat.start();

            // 3) 数据上报循环
            long deadline = System.currentTimeMillis() + durationSec * 1000L;
            long seq = 0;
            while (System.currentTimeMillis() < deadline) {
                sendRealtimeFrame(seq++);
                TimeUnit.MILLISECONDS.sleep(freqMs);
            }
            log.info("发送完成：帧 {} 条 / 数据 {} 条", sentFrames.get(), sentItems.get());
            return 0;
        } finally {
            if (channel != null) {
                channel.close().awaitUninterruptibly();
            }
            group.shutdownGracefully().awaitUninterruptibly();
        }
    }

    private void sendRegister() {
        long ts = System.currentTimeMillis();
        // 凭据项：PC=HMAC(PSK, GW_ID+TS) 前 32 字符，VALUE=协议版本×10^scale
        String digest = HmacUtil.hmacSha256Hex(psk, gatewayId + ts).substring(0, 32);
        long versionRaw = 1L * pow10(scale);
        TcpFrame frame = new TcpFrame(FrameType.REGISTER, gatewayId, ts,
                List.of(new TcpFrame.FrameItem(digest, 0, versionRaw)));
        channel.writeAndFlush(frame).awaitUninterruptibly();
    }

    private void heartbeatLoop() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                TimeUnit.SECONDS.sleep(heartbeatSec);
                TcpFrame frame = new TcpFrame(FrameType.HEARTBEAT, gatewayId,
                        System.currentTimeMillis(), List.of());
                channel.writeAndFlush(frame).awaitUninterruptibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void sendRealtimeFrame(long seq) {
        long ts = System.currentTimeMillis();
        List<TcpFrame.FrameItem> items = new ArrayList<>(pointCodes.size());
        int i = 0;
        for (String code : pointCodes) {
            // 确定性模拟值：基线 + 正弦波动 + 噪声（异常注入能力后续扩展）
            double value = 10.0 + 5.0 * Math.sin((seq + i) / 10.0) + (Math.random() - 0.5);
            long valueRaw = Math.round(value * pow10(scale));
            items.add(new TcpFrame.FrameItem(code, 0, valueRaw));
            i++;
        }
        channel.writeAndFlush(new TcpFrame(FrameType.REALTIME, gatewayId, ts, items))
                .awaitUninterruptibly();
        sentFrames.incrementAndGet();
        sentItems.addAndGet(items.size());
    }

    private static long pow10(int n) {
        long r = 1;
        for (int i = 0; i < n; i++) {
            r *= 10;
        }
        return r;
    }

    // ---------- 参数解析 ----------

    public static void main(String[] args) throws Exception {
        Builder b = new Builder();
        for (int i = 0; i < args.length; i++) {
            String k = args[i];
            String v = i + 1 < args.length ? args[i + 1] : null;
            switch (k) {
                case "--host" -> b.host(v);
                case "--port" -> b.port(Integer.parseInt(v));
                case "--gw" -> b.gatewayId(v);
                case "--psk" -> b.psk(v);
                case "--points" -> b.points(Arrays.asList(v.split(",")));
                case "--freq-ms" -> b.freqMs(Long.parseLong(v));
                case "--duration-sec" -> b.durationSec(Long.parseLong(v));
                case "--scale" -> b.scale(Integer.parseInt(v));
                case "--heartbeat-sec" -> b.heartbeatSec(Long.parseLong(v));
                default -> { }
            }
            if (v != null) {
                i++;
            }
        }
        if (b.invalid()) {
            System.err.println("用法: GatewaySimulator --host 127.0.0.1 --port 9000 --gw GW001 --psk <PSK> "
                    + "--points P00010001,P00010002 [--freq-ms 1000] [--duration-sec 60] [--scale 4] [--heartbeat-sec 30]");
            System.exit(2);
        }
        int code = new GatewaySimulator(b).run();
        System.exit(code);
    }

    /** 构建器（main 与测试共用） */
    static final class Builder {
        private String host = "127.0.0.1";
        private int port = 9000;
        private String gatewayId = "GW001";
        private String psk;
        private List<String> pointCodes = List.of();
        private long freqMs = 1000;
        private long durationSec = 60;
        private int scale = 4;
        private long heartbeatSec = 30;

        Builder host(String v) { this.host = v; return this; }
        Builder port(int v) { this.port = v; return this; }
        Builder gatewayId(String v) { this.gatewayId = v; return this; }
        Builder psk(String v) { this.psk = v; return this; }
        Builder points(List<String> v) { this.pointCodes = v; return this; }
        Builder freqMs(long v) { this.freqMs = v; return this; }
        Builder durationSec(long v) { this.durationSec = v; return this; }
        Builder scale(int v) { this.scale = v; return this; }
        Builder heartbeatSec(long v) { this.heartbeatSec = v; return this; }

        boolean invalid() {
            return psk == null || pointCodes.isEmpty();
        }
    }
}
