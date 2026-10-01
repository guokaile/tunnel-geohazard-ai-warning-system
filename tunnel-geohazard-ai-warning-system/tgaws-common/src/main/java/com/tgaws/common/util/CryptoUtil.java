package com.tgaws.common.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 加解密工具（带认证标签，防篡改）。
 *
 * <p>用途：mon_gateway.secret 与 sys_config.config_type=2 的密钥类字段密文存储
 * （消除"DDL 标注密文/实现明文"不一致——等保测评常见扣分点）。</p>
 *
 * <p>主密钥：环境变量 {@code CONFIG_ENC_KEY}（安装向导生成，随《环境变量说明》交付）；
 * 任意长度口令经 SHA-256 派生 32 字节密钥。密文格式：Base64(IV 12B + 密文+Tag)。</p>
 */
public final class CryptoUtil {

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_LEN = 32;

    private CryptoUtil() {
    }

    /** 主密钥环境变量名 */
    public static final String KEY_ENV = "CONFIG_ENC_KEY";

    /** 密文列宽（varchar 长度）：mon_gateway.secret 与 sys_third_app.app_secret 同规格 */
    public static final int SECRET_COLUMN_LEN = 128;

    /**
     * PSK 明文上限（字节）：密文=Base64(12B IV+明文+16B Tag)≈(28+n)×4/3≤128 → 理论 n≤68；
     * 取 64 保守值并限定字符集 [A-Za-z0-9+/=_-]（纯 ASCII，字符数=字节数，
     * 且避免不同环境编码差异导致同一 PSK 算出不同 HMAC——现场极难排查）。
     */
    public static final int PSK_MAX_BYTES = 64;

    private static final java.util.regex.Pattern PSK_PATTERN =
            java.util.regex.Pattern.compile("^[A-Za-z0-9+/=_-]{1," + PSK_MAX_BYTES + "}$");

    /** 密文列宽对应的理论明文上限（字节）——两个 128 列共用此口径（评审 3.1），改列宽只改一处 */
    public static int maxPlainBytes(int cipherColumnLen) {
        return cipherColumnLen * 3 / 4 - 28;
    }

    /**
     * PSK 校验（评审 3.2：按字节计——纯 ASCII 限定下字符数=字节数）：
     * 仅允许 [A-Za-z0-9+/=_-]，UTF-8 字节 ≤64；失败抛 BizException（A0002）。
     */
    public static void validateSecret(String plain) {
        if (plain == null || !PSK_PATTERN.matcher(plain).matches()) {
            throw new com.tgaws.common.exception.BizException(
                    com.tgaws.common.result.ErrorCode.A0002,
                    "PSK 格式非法：仅允许 [A-Za-z0-9+/=_-]，按 UTF-8 字节计上限 " + PSK_MAX_BYTES);
        }
    }

    /** 从环境变量读取主密钥（未配置抛异常，启动即失败，拒绝明文降级） */
    public static String masterKey() {
        String key = System.getenv(KEY_ENV);
        if (key == null || key.isEmpty()) {
            throw new IllegalStateException("环境变量 " + KEY_ENV + " 未配置（密钥管理：禁止明文降级）");
        }
        return key;
    }

    /** 加密（返回 Base64） */
    public static String encrypt(String plain, String masterKey) {
        try {
            byte[] iv = new byte[IV_LEN];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(deriveKey(masterKey), "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_LEN + cipherBytes.length];
            System.arraycopy(iv, 0, out, 0, IV_LEN);
            System.arraycopy(cipherBytes, 0, out, IV_LEN, cipherBytes.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    /** 解密（篡改/密钥错误抛异常——GCM 认证标签校验） */
    public static String decrypt(String cipherB64, String masterKey) {
        try {
            byte[] in = Base64.getDecoder().decode(cipherB64);
            if (in.length < IV_LEN + 16) {
                throw new IllegalArgumentException("密文格式非法");
            }
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(in, 0, iv, 0, IV_LEN);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(deriveKey(masterKey), "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(in, IV_LEN, in.length - IV_LEN), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalArgumentException("解密失败（密文被篡改或主密钥错误）", e);
        }
    }

    /** SHA-256 派生 32 字节密钥（口令 → 定长密钥） */
    private static byte[] deriveKey(String masterKey) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(masterKey.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("密钥派生失败", e);
        }
    }
}
