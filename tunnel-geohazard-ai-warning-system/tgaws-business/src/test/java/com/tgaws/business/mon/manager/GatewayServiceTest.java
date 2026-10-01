package com.tgaws.business.mon.manager;

import com.tgaws.business.mon.entity.GatewayEntity;
import com.tgaws.business.mon.mapper.GatewayCrudMapper;
import com.tgaws.business.mon.vo.GatewayVo;
import com.tgaws.common.exception.BizException;
import com.tgaws.common.util.CryptoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 网关台账服务单测（评审 4.1~4.4）：
 * ①写明文→落库密文→可解密 ②PSK 字节级校验+字符集限定 ③VO 无 secret 字段
 * ④更新"不传=不变更" ⑤列表不解密仅布尔标志。
 */
class GatewayServiceTest {

    private static final String KEY = "unit-test-enc-key";

    private GatewayCrudMapper mapper;
    private GatewayService service;

    @BeforeEach
    void setUp() {
        mapper = mock(GatewayCrudMapper.class);
        service = new GatewayService(mapper, KEY);
    }

    @Test
    void createEncryptsSecretAndRoundTripDecrypts() {
        service.create(new GatewayService.CreateCmd("GW001", "测试网关", "t2-psk-123", 1, 4, 1));
        ArgumentCaptor<GatewayEntity> captor = ArgumentCaptor.forClass(GatewayEntity.class);
        verify(mapper).insert(captor.capture());
        String stored = captor.getValue().getSecret();
        assertNotEquals("t2-psk-123", stored, "落库必须为密文（评审 4.1：拒绝明文落库）");
        assertTrue(stored.length() <= CryptoUtil.SECRET_COLUMN_LEN, "密文不超 varchar(128)");
        assertEquals("t2-psk-123", CryptoUtil.decrypt(stored, KEY), "密文可解回明文（单一加解密路径）");
    }

    @Test
    void invalidPskRejectedByByteCharsetRule() {
        // 中文：UTF-8 字节超限/字符集不符
        assertThrows(BizException.class, () -> service.create(
                new GatewayService.CreateCmd("GW001", "n", "密钥中文测试", 1, 4, 1)));
        // 超长（65 字符）
        String longPsk = "a".repeat(65);
        assertThrows(BizException.class, () -> service.create(
                new GatewayService.CreateCmd("GW001", "n", longPsk, 1, 4, 1)));
        // 合法 64 字符边界
        service.create(new GatewayService.CreateCmd("GW001", "n", "a".repeat(64), 1, 4, 1));
        verify(mapper).insert(any(GatewayEntity.class));
    }

    @Test
    void updateWithoutSecretKeepsOldValue() {
        when(mapper.selectById(1L)).thenReturn(entityWithSecret());
        service.update(1L, new GatewayService.UpdateCmd("改名", null, 1, 4));
        ArgumentCaptor<GatewayEntity> captor = ArgumentCaptor.forClass(GatewayEntity.class);
        verify(mapper).update(captor.capture());
        assertEquals(null, captor.getValue().getSecret(), "不传=不变更：secret 置 null 由 XML 跳过该列");
        assertEquals("改名", captor.getValue().getGatewayName());
    }

    @Test
    void listVoHasNoSecretField() throws NoSuchFieldException {
        // 类型层隔离：VO 不存在 secret 字段（编译期保障，此处反射断言）
        assertThrows(NoSuchFieldException.class, () -> GatewayVo.class.getDeclaredField("secret"));
        when(mapper.selectList(null)).thenReturn(List.of(entityWithSecretConfigured()));
        List<GatewayVo> vos = service.list(null);
        assertEquals(Boolean.TRUE, vos.get(0).secretConfigured(), "列表仅布尔标志，不解密不返回密钥");
    }

    @Test
    void secretConfiguredFlagReflectsPresence() {
        when(mapper.selectList(null)).thenReturn(List.of(entityWithSecretConfigured()));
        assertEquals(Boolean.TRUE, service.list(null).get(0).secretConfigured());
    }

    private static GatewayEntity entityWithSecret() {
        GatewayEntity entity = new GatewayEntity();
        entity.setId(1L);
        entity.setGatewayCode("GW001");
        entity.setGatewayName("网关");
        entity.setSecret("encrypted-blob");
        entity.setProtocol(1);
        entity.setScale(4);
        entity.setStatus(1);
        return entity;
    }

    private static GatewayEntity entityWithSecretConfigured() {
        GatewayEntity entity = new GatewayEntity();
        entity.setId(1L);
        entity.setGatewayCode("GW001");
        entity.setGatewayName("网关");
        entity.setProtocol(1);
        entity.setScale(4);
        entity.setStatus(1);
        entity.setSecretConfigured(Boolean.TRUE);
        return entity;
    }
}
