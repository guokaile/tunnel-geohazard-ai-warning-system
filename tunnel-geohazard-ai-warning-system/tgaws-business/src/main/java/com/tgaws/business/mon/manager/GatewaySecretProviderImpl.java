package com.tgaws.business.mon.manager;

import com.tgaws.common.gateway.GatewayCredential;
import com.tgaws.common.gateway.IGatewaySecretProvider;
import com.tgaws.business.mon.entity.GatewayEntity;
import com.tgaws.business.mon.mapper.GatewayMapper;
import com.tgaws.common.util.CryptoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * IGatewaySecretProvider 业务实现（W4-4c 接线：mon_gateway 查询 + CryptoUtil 密文解密）。
 *
 * <p>解密主密钥为环境变量 CONFIG_ENC_KEY；未配置时注册认证不可用（拒绝明文降级，
 * 与《3》附录C/《6》附录A 密钥管理口径一致）。</p>
 */
@Component
public class GatewaySecretProviderImpl implements IGatewaySecretProvider {

    private static final Logger log = LoggerFactory.getLogger(GatewaySecretProviderImpl.class);

    private final GatewayMapper gatewayMapper;

    /** 与 GatewayService 写入共用同一密钥来源（评审 4.1：加解密判定只有一处口径） */
    private final String masterKey;

    public GatewaySecretProviderImpl(GatewayMapper gatewayMapper,
                                     @Value("${config.enc.key:}") String masterKey) {
        this.gatewayMapper = gatewayMapper;
        this.masterKey = masterKey;
    }

    @Override
    public GatewayCredential credentialOf(String gatewayCode) {
        GatewayEntity entity = gatewayMapper.selectByCode(gatewayCode);
        if (entity == null || entity.getStatus() == null || entity.getStatus() != 1) {
            return null;
        }
        if (masterKey == null || masterKey.isEmpty()) {
            log.error("网关 {} 注册认证不可用：config.enc.key 未配置（禁止明文降级）", gatewayCode);
            return null;
        }
        try {
            String plainSecret = CryptoUtil.decrypt(entity.getSecret(), masterKey);
            return new GatewayCredential(plainSecret, true);
        } catch (IllegalArgumentException e) {
            log.error("网关 {} 凭据解密失败（密文被篡改或主密钥错误）", gatewayCode);
            return null;
        }
    }
}
