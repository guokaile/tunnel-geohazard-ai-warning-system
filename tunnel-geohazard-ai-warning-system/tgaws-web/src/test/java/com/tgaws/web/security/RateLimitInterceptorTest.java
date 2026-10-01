package com.tgaws.web.security;

import com.tgaws.business.sys.config.SysConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 接口分级限流拦截器单测（T-703：分级默认/sys_config 覆盖/按用户隔离/429）。
 */
class RateLimitInterceptorTest {

    private SysConfigService sysConfigService;
    private RateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        sysConfigService = mock(SysConfigService.class);
        interceptor = new RateLimitInterceptor(sysConfigService);
    }

    @AfterEach
    void tearDown() {
        LoginContext.clear();
    }

    private HandlerMethod writeHandler() throws NoSuchMethodException {
        return new HandlerMethod(new Stub(), Stub.class.getMethod("write"));
    }

    private HandlerMethod noLimitHandler() throws NoSuchMethodException {
        return new HandlerMethod(new Stub(), Stub.class.getMethod("noLimit"));
    }

    @Test
    void underLimitPasses() throws Exception {
        LoginContext.setUser(new LoginContext.LoginUser(1L, "u", Set.of(), Set.of(), Set.of(), 2));
        assertTrue(interceptor.preHandle(new MockHttpServletRequest(),
                new MockHttpServletResponse(), writeHandler()));
    }

    @Test
    void overLimitRejected429() throws Exception {
        LoginContext.setUser(new LoginContext.LoginUser(1L, "u", Set.of(), Set.of(), Set.of(), 2));
        MockHttpServletResponse last = new MockHttpServletResponse();
        for (int i = 0; i < 3; i++) { // WRITE 默认 2/s
            last = new MockHttpServletResponse();
            interceptor.preHandle(new MockHttpServletRequest(), last, writeHandler());
        }
        assertEquals(429, last.getStatus(), "第 3 次超限 → 429");
        assertTrue(last.getContentAsString().contains("请求频率超限"));
    }

    @Test
    void perUserIsolation() throws Exception {
        LoginContext.setUser(new LoginContext.LoginUser(1L, "u", Set.of(), Set.of(), Set.of(), 2));
        interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), writeHandler());
        interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), writeHandler());
        // 另一用户不受影响
        LoginContext.setUser(new LoginContext.LoginUser(2L, "v", Set.of(), Set.of(), Set.of(), 2));
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(new MockHttpServletRequest(), response, writeHandler()));
        assertEquals(200, response.getStatus());
    }

    @Test
    void sysConfigOverridesDefaultLimit() throws Exception {
        when(sysConfigService.getInt("rate.limit.write", 2)).thenReturn(1);
        LoginContext.setUser(new LoginContext.LoginUser(1L, "u", Set.of(), Set.of(), Set.of(), 2));
        MockHttpServletResponse second = new MockHttpServletResponse();
        interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), writeHandler());
        interceptor.preHandle(new MockHttpServletRequest(), second, writeHandler());
        assertEquals(429, second.getStatus(), "sys_config rate.limit.write=1 覆盖默认 2");
    }

    @Test
    void unannotatedHandlerNotLimited() throws Exception {
        LoginContext.setUser(new LoginContext.LoginUser(1L, "u", Set.of(), Set.of(), Set.of(), 2));
        for (int i = 0; i < 50; i++) {
            assertTrue(interceptor.preHandle(new MockHttpServletRequest(),
                    new MockHttpServletResponse(), noLimitHandler()));
        }
    }

    @Test
    void loginTierCountsByIp() throws Exception {
        // 未登录（登录接口场景）：按 IP 计数，5/min 默认
        MockHttpServletResponse last = new MockHttpServletResponse();
        HandlerMethod login = new HandlerMethod(new Stub(), Stub.class.getMethod("login"));
        for (int i = 0; i < 6; i++) {
            last = new MockHttpServletResponse();
            interceptor.preHandle(new MockHttpServletRequest(), last, login);
        }
        assertEquals(429, last.getStatus());
    }

    @Test
    void tierDefaults() {
        assertEquals(10, RateLimitInterceptor.defaultLimit(RateLimit.Tier.DASHBOARD));
        assertEquals(5, RateLimitInterceptor.defaultLimit(RateLimit.Tier.QUERY));
        assertEquals(2, RateLimitInterceptor.defaultLimit(RateLimit.Tier.WRITE));
        assertEquals(5, RateLimitInterceptor.defaultLimit(RateLimit.Tier.LOGIN));
        assertEquals(60, RateLimitInterceptor.windowSeconds(RateLimit.Tier.LOGIN));
    }

    static class Stub {
        @RateLimit(RateLimit.Tier.WRITE)
        public void write() {
        }

        @RateLimit(RateLimit.Tier.QUERY)
        public void query() {
        }

        @RateLimit(RateLimit.Tier.LOGIN)
        public void login() {
        }

        public void noLimit() {
        }
    }
}
