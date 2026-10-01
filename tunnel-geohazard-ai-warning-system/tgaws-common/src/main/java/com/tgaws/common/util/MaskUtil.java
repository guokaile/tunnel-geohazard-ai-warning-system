package com.tgaws.common.util;

/**
 * 敏感信息脱敏工具（《6.详细设计说明书》6.3.7 @OperLog 审计与日志脱敏）。
 */
public final class MaskUtil {

    private MaskUtil() {
    }

    /** 口令类：一律掩码为固定串 */
    public static String maskPassword(String value) {
        return maskFully(value);
    }

    /** 密钥/令牌类：保留前 4 后 4，中间掩码 */
    public static String maskSecret(String value) {
        if (value == null || value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    /** 手机号：138****1234 */
    public static String maskPhone(String value) {
        if (value == null || value.length() != 11) {
            return maskFully(value);
        }
        return value.substring(0, 3) + "****" + value.substring(7);
    }

    /** 全掩码（null 安全，统一返回固定掩码串） */
    public static String maskFully(String value) {
        return "******";
    }
}
