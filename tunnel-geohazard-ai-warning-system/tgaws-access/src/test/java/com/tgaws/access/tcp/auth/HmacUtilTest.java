package com.tgaws.access.tcp.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * HMAC-SHA256 单测（RFC 4231 标准测试向量）。
 */
class HmacUtilTest {

    @Test
    void rfc4231TestVector() {
        // RFC 4231 Test Case 2（key="Jefe"，data="what do ya want for nothing?"）
        String hex = HmacUtil.hmacSha256Hex("Jefe", "what do ya want for nothing?");
        assertEquals("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843", hex);
    }

    @Test
    void deterministicOutput() {
        String a = HmacUtil.hmacSha256Hex("psk", "GW001123");
        String b = HmacUtil.hmacSha256Hex("psk", "GW001123");
        assertEquals(a, b);
        assertEquals(64, a.length(), "HEX 小写 64 字符");
    }
}
