package com.tgaws.business.sys.auth;

import com.tgaws.business.sys.mapper.ThirdAppMapper;
import com.tgaws.common.util.CryptoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 第三方应用服务单测（T-702：密文解密/停用过期拒绝/轮换过渡窗口/缓存）。
 */
class ThirdAppServiceTest {

    private static final String KEY = "dev-t2-key";

    private ThirdAppMapper thirdAppMapper;
    private ThirdAppService service;

    @BeforeEach
    void setUp() {
        thirdAppMapper = mock(ThirdAppMapper.class);
        service = new ThirdAppService(thirdAppMapper, KEY);
    }

    private ThirdAppMapper.ThirdAppRow row(String appKey, String secretPlain) {
        return new ThirdAppMapper.ThirdAppRow(appKey, CryptoUtil.encrypt(secretPlain, KEY),
                null, null, "瓦斯监控", 1, null, null);
    }

    @Test
    void credentialDecryptsSecret() {
        when(thirdAppMapper.selectByAppKey("GAS-01")).thenReturn(row("GAS-01", "s3cret"));
        ThirdAppService.AppCredential c = service.credential("GAS-01");
        assertNotNull(c);
        assertEquals("s3cret", c.secret());
        assertEquals(Set.of(), c.ipWhitelist());
        assertNull(c.secretOld());
    }

    @Test
    void unknownDisabledExpiredAllRejected() {
        when(thirdAppMapper.selectByAppKey("X")).thenReturn(null);
        assertNull(service.credential("X"), "不存在 → null（防枚举）");

        ThirdAppMapper.ThirdAppRow disabled = row("D", "s");
        when(thirdAppMapper.selectByAppKey("D"))
                .thenReturn(new ThirdAppMapper.ThirdAppRow("D", disabled.appSecret(), null,
                        null, "x", 0, null, null));
        assertNull(service.credential("D"), "停用 → null");

        ThirdAppMapper.ThirdAppRow expired = row("E", "s");
        when(thirdAppMapper.selectByAppKey("E"))
                .thenReturn(new ThirdAppMapper.ThirdAppRow("E", expired.appSecret(), null,
                        null, "x", 1, LocalDateTime.now().minusDays(1), null));
        assertNull(service.credential("E"), "过期 → null");
    }

    @Test
    void rotationWindowKeepsOldSecretParallel24h() {
        ThirdAppMapper.ThirdAppRow r = new ThirdAppMapper.ThirdAppRow(
                "GAS-02", CryptoUtil.encrypt("new-secret", KEY),
                CryptoUtil.encrypt("old-secret", KEY),
                LocalDateTime.now().minusHours(2), "瓦斯监控", 1, null, "10.0.0.1,10.0.0.2");
        when(thirdAppMapper.selectByAppKey("GAS-02")).thenReturn(r);
        ThirdAppService.AppCredential c = service.credential("GAS-02");
        assertEquals("new-secret", c.secret());
        assertEquals("old-secret", c.secretOld(), "24h 过渡窗口内旧密钥并行");
        assertEquals(Set.of("10.0.0.1", "10.0.0.2"), c.ipWhitelist());
    }

    @Test
    void rotationBeyond24hDropsOldSecret() {
        ThirdAppMapper.ThirdAppRow r = new ThirdAppMapper.ThirdAppRow(
                "GAS-03", CryptoUtil.encrypt("new-secret", KEY),
                CryptoUtil.encrypt("old-secret", KEY),
                LocalDateTime.now().minusHours(25), "瓦斯监控", 1, null, null);
        when(thirdAppMapper.selectByAppKey("GAS-03")).thenReturn(r);
        assertNull(service.credential("GAS-03").secretOld(), "超 24h 旧密钥失效");
    }

    @Test
    void credentialCachedWithin60s() {
        when(thirdAppMapper.selectByAppKey("GAS-01")).thenReturn(row("GAS-01", "s3cret"));
        service.credential("GAS-01");
        service.credential("GAS-01");
        verify(thirdAppMapper, times(1)).selectByAppKey("GAS-01");
    }
}
