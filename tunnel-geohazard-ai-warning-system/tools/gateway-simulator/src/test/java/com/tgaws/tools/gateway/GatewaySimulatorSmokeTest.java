package com.tgaws.tools.gateway;

import com.tgaws.access.tcp.TcpServer;
import com.tgaws.common.gateway.GatewayCredential;
import com.tgaws.common.pipeline.DataFrame;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网关模拟器 ↔ TcpServer 端到端冒烟（T-902b 离线联调雏形）：
 * 模拟器注册（HMAC 凭据）→ ACK 成功 → 持续上报 → 服务端管道收到数据帧。
 */
class GatewaySimulatorSmokeTest {

    private static final String PSK = "smoke-test-psk";

    @Test
    void registerAndReportEndToEnd() throws Exception {
        List<DataFrame> received = new CopyOnWriteArrayList<>();
        TcpServer server = new TcpServer(0, received::add,
                code -> "GW001".equals(code) ? new GatewayCredential(PSK, true) : null, 4);
        server.start();
        try {
            int port = server.boundPort();
            int exitCode = new GatewaySimulator(new GatewaySimulator.Builder()
                    .host("127.0.0.1").port(port).gatewayId("GW001").psk(PSK)
                    .points(List.of("P00010001", "P00010002"))
                    .freqMs(100).durationSec(1).scale(4))
                    .run();
            assertEquals(0, exitCode, "注册成功且完成发送");
            // 等待管道异步落盘
            TimeUnit.MILLISECONDS.sleep(500);
            assertTrue(received.size() >= 2, "应收到至少 2 条数据帧，实际 " + received.size());
            assertEquals("GW001", received.get(0).gatewayCode());
            assertTrue(received.get(0).value().doubleValue() > 0, "模拟值应为正数");
            // 心跳与注册已刷新在线状态
            assertEquals(System.currentTimeMillis(), server.onlineRegistry().lastSeenOf("GW001"), 5000L);
        } finally {
            server.stop();
        }
    }

    @Test
    void wrongPskFailsWithResult4() throws Exception {
        TcpServer server = new TcpServer(0, f -> { },
                code -> "GW001".equals(code) ? new GatewayCredential(PSK, true) : null, 4);
        server.start();
        try {
            int exitCode = new GatewaySimulator(new GatewaySimulator.Builder()
                    .host("127.0.0.1").port(server.boundPort()).gatewayId("GW001").psk("wrong-psk")
                    .points(List.of("P00010001")).freqMs(100).durationSec(1))
                    .run();
            assertEquals(4, exitCode, "凭据错误 → 退出码 4");
        } finally {
            server.stop();
        }
    }
}
