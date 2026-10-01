package com.tgaws.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 脱敏工具单测（审计日志/日志输出的敏感性保障）。
 */
class MaskUtilTest {

    @Test
    void maskPasswordAlwaysFullyMasked() {
        assertEquals("******", MaskUtil.maskPassword("any-secret"));
        assertEquals("******", MaskUtil.maskPassword(null));
    }

    @Test
    void maskSecretKeepsFirst4Last4() {
        assertEquals("abcd****wxyz", MaskUtil.maskSecret("abcdefghwxyz"));
        assertEquals("****", MaskUtil.maskSecret("short"));
        assertEquals("****", MaskUtil.maskSecret(null));
    }

    @Test
    void maskPhoneStandard() {
        assertEquals("138****1234", MaskUtil.maskPhone("13812341234"));
        // 非 11 位输入不做部分掩码，全掩码兜底
        assertEquals("******", MaskUtil.maskPhone("12345"));
        assertEquals("******", MaskUtil.maskPhone(null));
    }
}
