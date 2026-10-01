package com.tgaws.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * AES-256-GCM 加解密单测（mon_gateway.secret / sys_config 密钥类字段密文路径）。
 */
class CryptoUtilTest {

    private static final String KEY = "unit-test-master-key";

    @Test
    void roundTrip() {
        String plain = "gw-psk-123456";
        String cipher = CryptoUtil.encrypt(plain, KEY);
        assertNotEquals(plain, cipher, "密文不得泄露明文");
        assertEquals(plain, CryptoUtil.decrypt(cipher, KEY));
    }

    @Test
    void tamperedCipherRejected() {
        String cipher = CryptoUtil.encrypt("secret-value", KEY);
        String tampered = cipher.substring(0, cipher.length() - 2) + "AA";
        assertThrows(IllegalArgumentException.class,
                () -> CryptoUtil.decrypt(tampered, KEY), "GCM 认证标签应拒绝篡改密文");
    }

    @Test
    void wrongKeyRejected() {
        String cipher = CryptoUtil.encrypt("secret-value", KEY);
        assertThrows(IllegalArgumentException.class,
                () -> CryptoUtil.decrypt(cipher, "another-key"), "错误主密钥应解密失败");
    }
}
