package com.tgaws.business.mon.manager;

import com.tgaws.business.mon.entity.GatewayEntity;
import com.tgaws.business.mon.mapper.GatewayCrudMapper;
import com.tgaws.business.mon.vo.GatewayVo;
import com.tgaws.common.exception.BizException;
import com.tgaws.common.result.ErrorCode;
import com.tgaws.common.util.CryptoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关台账服务（T-602，评审 4.1~4.4 全部落地）：
 *
 * <ul>
 *   <li><b>单一加解密路径</b>：写入统一走 CryptoUtil.encrypt（与
 *       GatewaySecretProviderImpl 的解密共用同一密钥来源 config.enc.key，
 *       加解密与"拒绝明文降级"只有一处判定）；</li>
 *   <li><b>写入校验</b>：PSK 按 UTF-8 字节 ≤64 且字符集 [A-Za-z0-9+/=_-]（CryptoUtil.validateSecret）；</li>
 *   <li><b>读路径</b>：VO 类型层无 secret 字段（密文绝不外泄）；列表仅 secretConfigured 布尔（不解密）；</li>
 *   <li><b>更新语义</b>：secret 不传 = 不变更（评审 4.4，运维改名称不得清掉 PSK 导致网关联不上）。</li>
 * </ul>
 */
@Service
public class GatewayService {

    private static final Logger log = LoggerFactory.getLogger(GatewayService.class);

    private final GatewayCrudMapper gatewayMapper;
    private final String masterKey;

    public GatewayService(GatewayCrudMapper gatewayMapper,
                          @Value("${config.enc.key:}") String masterKey) {
        if (masterKey == null || masterKey.isEmpty()) {
            throw new IllegalStateException("config.enc.key 未配置（拒绝明文降级，与 GatewaySecretProviderImpl 同口径）");
        }
        this.gatewayMapper = gatewayMapper;
        this.masterKey = masterKey;
    }

    /** 新增（PSK 必填，加密落库） */
    @Transactional
    public Long create(CreateCmd cmd) {
        CryptoUtil.validateSecret(cmd.secret());
        GatewayEntity entity = new GatewayEntity();
        entity.setGatewayCode(cmd.gatewayCode());
        entity.setGatewayName(cmd.gatewayName());
        entity.setSecret(CryptoUtil.encrypt(cmd.secret(), masterKey));
        entity.setProtocol(cmd.protocol() == null ? 1 : cmd.protocol());
        entity.setScale(cmd.scale() == null ? 4 : cmd.scale());
        entity.setStatus(cmd.status() == null ? 1 : cmd.status());
        gatewayMapper.insert(entity);
        log.info("网关新增：{}（PSK 已密文落库）", cmd.gatewayCode());
        return entity.getId();
    }

    /** 修改（secret 不传=不变更） */
    @Transactional
    public void update(long id, UpdateCmd cmd) {
        GatewayEntity entity = gatewayMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.B0112);
        }
        entity.setGatewayName(cmd.gatewayName());
        entity.setProtocol(cmd.protocol());
        entity.setScale(cmd.scale());
        if (cmd.secret() != null) {
            CryptoUtil.validateSecret(cmd.secret());
            entity.setSecret(CryptoUtil.encrypt(cmd.secret(), masterKey));
        } else {
            entity.setSecret(null); // 不传=不变更（XML <if> 跳过该列）
        }
        gatewayMapper.update(entity);
    }

    /** 列表（无任何密钥形态） */
    public List<GatewayVo> list(String keyword) {
        List<GatewayEntity> entities = gatewayMapper.selectList(keyword);
        List<GatewayVo> vos = new ArrayList<>(entities.size());
        for (GatewayEntity e : entities) {
            vos.add(toVo(e));
        }
        return vos;
    }

    /** 详情（无密钥字段） */
    public GatewayVo get(long id) {
        GatewayEntity entity = gatewayMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.B0112);
        }
        return toVo(entity);
    }

    public void updateStatus(long id, int status) {
        gatewayMapper.updateStatus(id, status);
    }

    public void delete(long id) {
        gatewayMapper.logicDelete(id);
    }

    private static GatewayVo toVo(GatewayEntity e) {
        return new GatewayVo(e.getId(), e.getGatewayCode(), e.getGatewayName(),
                e.getProtocol(), e.getScale(), e.getStatus(), e.getOnlineStatus(),
                e.getLastOnlineTime(), e.getSecretConfigured());
    }

    /** 新增命令 */
    public record CreateCmd(String gatewayCode, String gatewayName, String secret,
                            Integer protocol, Integer scale, Integer status) {
    }

    /** 修改命令（secret 为 null 表示不变更） */
    public record UpdateCmd(String gatewayName, String secret,
                            Integer protocol, Integer scale) {
    }
}
