package com.tgaws.business.sys.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tgaws.business.sys.mapper.ThirdAppMapper;
import com.tgaws.common.util.CryptoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 第三方应用服务（T-702，Open API 签名校验用）：
 *
 * <ul>
 *   <li>appKey → 注册信息（60s 缓存，密钥轮换 ≤60s 生效）；</li>
 *   <li>appSecret 密文存储，config.enc.key 解密（与网关注册凭据同一主密钥）；</li>
 *   <li>轮换过渡：secret_rotate_time 后 24h 内新旧密钥并行验签；</li>
 *   <li>校验口径：不存在/停用/过期统一返回 null（不泄露注册存在性）。</li>
 * </ul>
 */
@Service
public class ThirdAppService {

    private static final Logger log = LoggerFactory.getLogger(ThirdAppService.class);

    private final ThirdAppMapper thirdAppMapper;
    private final String masterKey;
    private final Cache<String, AppCredential> cache = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(Duration.ofSeconds(60))
            .build();

    public ThirdAppService(ThirdAppMapper thirdAppMapper,
                           @Value("${config.enc.key:}") String masterKey) {
        this.thirdAppMapper = thirdAppMapper;
        this.masterKey = masterKey;
    }

    /**
     * 取应用凭据（签名校验口径）。
     *
     * @return null=不存在/停用/过期/密钥不可用（统一拒绝，防枚举）
     */
    public AppCredential credential(String appKey) {
        if (masterKey == null || masterKey.isBlank()) {
            log.error("Open API 验签不可用：config.enc.key 未配置（禁止明文降级）");
            return null;
        }
        return cache.get(appKey, key -> load(key, masterKey));
    }

    private AppCredential load(String appKey, String key) {
        ThirdAppMapper.ThirdAppRow row = thirdAppMapper.selectByAppKey(appKey);
        if (row == null || row.enabled() == null || row.enabled() != 1) {
            return null;
        }
        if (row.expireTime() != null && row.expireTime().isBefore(LocalDateTime.now())) {
            return null;
        }
        String secret = decryptQuietly(row.appSecret(), key);
        if (secret == null) {
            return null;
        }
        String secretOld = null;
        // 轮换过渡窗口：24h 内旧密钥并行有效
        if (row.secretRotateTime() != null && row.secretOld() != null
                && row.secretRotateTime().isAfter(LocalDateTime.now().minusHours(24))) {
            secretOld = decryptQuietly(row.secretOld(), key);
        }
        Set<String> ipWhitelist = row.ipWhitelist() == null || row.ipWhitelist().isBlank()
                ? Set.of() : Set.of(row.ipWhitelist().split(","));
        return new AppCredential(appKey, secret, secretOld, ipWhitelist);
    }

    private String decryptQuietly(String ciphertext, String key) {
        try {
            return CryptoUtil.decrypt(ciphertext, key);
        } catch (Exception e) {
            log.error("第三方应用密钥解密失败（密文与主密钥不匹配或数据损坏）", e);
            return null;
        }
    }

    /**
     * 验签凭据（ipWhitelist 空集合=不限来源）。
     *
     * @param secretOld null=无轮换过渡
     */
    public record AppCredential(String appKey, String secret, String secretOld,
                                Set<String> ipWhitelist) {
    }
}
