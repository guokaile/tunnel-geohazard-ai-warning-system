package com.tgaws.web.filter;

import com.tgaws.business.sys.auth.ThirdAppService;
import com.tgaws.common.result.ErrorCode;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Open API 签名过滤器单测（T-702 验收：签名错 A0004 / 时间窗 A0005 / nonce 重放拒绝）。
 */
class OpenApiSignFilterTest {

    private ThirdAppService thirdAppService;
    private OpenApiSignFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        thirdAppService = mock(ThirdAppService.class);
        filter = new OpenApiSignFilter(thirdAppService);
        chain = mock(FilterChain.class);
    }

    private MockHttpServletRequest signedRequest(String secret, String body) {
        return signedRequest(secret, body, "nonce-" + System.nanoTime());
    }

    private MockHttpServletRequest signedRequest(String secret, String body, String nonce) {
        long ts = System.currentTimeMillis();
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/open/v1/data/push");
        req.setContent(body.getBytes(StandardCharsets.UTF_8));
        req.addHeader("X-App-Key", "GAS-01");
        req.addHeader("X-Timestamp", String.valueOf(ts));
        req.addHeader("X-Nonce", nonce);
        req.addHeader("X-Sign", OpenApiSignFilter.hmacSha256Hex(secret,
                ts + nonce + "POST" + "/open/v1/data/push" + body));
        return req;
    }

    private void stubCredential(String secret) {
        when(thirdAppService.credential("GAS-01"))
                .thenReturn(new ThirdAppService.AppCredential("GAS-01", secret, null, Set.of()));
    }

    @Test
    void missingHeadersRejectedA0004() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST", "/open/v1/data/push"), response, chain);
        assertEquals(ErrorCode.A0004.getCode(),
                jsonField(response, "code"), "缺签名头 → A0004");
        verify(chain, times(0)).doFilter(any(), any());
    }

    @Test
    void timeWindowExceededRejectedA0005() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/open/v1/data/push");
        req.addHeader("X-App-Key", "GAS-01");
        req.addHeader("X-Timestamp", String.valueOf(System.currentTimeMillis() - 10 * 60 * 1000L));
        req.addHeader("X-Nonce", "n1");
        req.addHeader("X-Sign", "abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(req, response, chain);
        assertEquals(ErrorCode.A0005.getCode(), jsonField(response, "code"), "5min 超窗 → A0005");
    }

    @Test
    void badSignRejectedA0004() throws Exception {
        stubCredential("s3cret");
        MockHttpServletRequest req = signedRequest("s3cret", "{\"batchNo\":\"B1\"}");
        req.removeHeader("X-Sign");
        req.addHeader("X-Sign", "deadbeef");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(req, response, chain);
        assertEquals(ErrorCode.A0004.getCode(), jsonField(response, "code"), "签名无效 → A0004");
        verify(chain, times(0)).doFilter(any(), any());
    }

    @Test
    void unknownAppRejectedA0004() throws Exception {
        when(thirdAppService.credential("GAS-01")).thenReturn(null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(signedRequest("s3cret", "{}"), response, chain);
        assertEquals(ErrorCode.A0004.getCode(), jsonField(response, "code"), "未注册 → A0004");
    }

    @Test
    void ipWhitelistRejectsForeignSource() throws Exception {
        when(thirdAppService.credential("GAS-01"))
                .thenReturn(new ThirdAppService.AppCredential("GAS-01", "s3cret", null,
                        Set.of("10.0.0.1")));
        MockHttpServletRequest req = signedRequest("s3cret", "{}");
        req.setRemoteAddr("192.168.1.5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(req, response, chain);
        assertEquals(ErrorCode.A0004.getCode(), jsonField(response, "code"), "IP 不在白名单 → A0004");
    }

    @Test
    void validRequestPassesAndBodyFlows() throws Exception {
        stubCredential("s3cret");
        String body = "{\"batchNo\":\"B1\",\"items\":[]}";
        MockHttpServletRequest req = signedRequest("s3cret", body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(req, response, chain);
        assertEquals(200, response.getStatus());
        verify(chain, times(1)).doFilter(any(), any());
        assertEquals("GAS-01", req.getAttribute("openAppKey"));
        assertNotNull(req.getInputStream(), "验签后 body 透传下游");
    }

    @Test
    void nonceReplayRejectedA0004() throws Exception {
        stubCredential("s3cret");
        String body = "{}";
        MockHttpServletRequest first = signedRequest("s3cret", body);
        filter.doFilter(first, new MockHttpServletResponse(), chain);

        // 同 nonce 重放（新时间戳签名正确也不行）
        long ts = System.currentTimeMillis();
        String nonce = first.getHeader("X-Nonce");
        MockHttpServletRequest replay = new MockHttpServletRequest("POST", "/open/v1/data/push");
        replay.setContent(body.getBytes(StandardCharsets.UTF_8));
        replay.addHeader("X-App-Key", "GAS-01");
        replay.addHeader("X-Timestamp", String.valueOf(ts));
        replay.addHeader("X-Nonce", nonce);
        replay.addHeader("X-Sign", OpenApiSignFilter.hmacSha256Hex("s3cret",
                ts + nonce + "POST" + "/open/v1/data/push" + body));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(replay, response, chain);
        assertEquals(ErrorCode.A0004.getCode(), jsonField(response, "code"), "nonce 重放 → A0004");
        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    void rateLimitOver100Rejected429() throws Exception {
        stubCredential("s3cret");
        MockHttpServletResponse last = new MockHttpServletResponse();
        for (int i = 0; i < 101; i++) {
            MockHttpServletRequest req = signedRequest("s3cret", "{}", "n-" + i);
            last = new MockHttpServletResponse();
            filter.doFilter(req, last, chain);
        }
        assertEquals(429, last.getStatus(), "单 app 100/min 超限 → 429");
    }

    @Test
    void oldSecretAcceptedInRotationWindow() throws Exception {
        when(thirdAppService.credential("GAS-01"))
                .thenReturn(new ThirdAppService.AppCredential("GAS-01", "new-secret",
                        "old-secret", Set.of()));
        // 用旧密钥签名：轮换过渡窗口应通过
        MockHttpServletRequest req = signedRequest("old-secret", "{}");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(req, response, chain);
        assertEquals(200, response.getStatus());
        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    void hmacHexLowercaseDeterministic() {
        String a = OpenApiSignFilter.hmacSha256Hex("k", "data");
        String b = OpenApiSignFilter.hmacSha256Hex("k", "data");
        assertEquals(a, b);
        assertEquals(a, a.toLowerCase(), "Hex 小写约定");
        assertTrue(OpenApiSignFilter.constantTimeEquals(a, b));
    }

    private String jsonField(MockHttpServletResponse response, String field) throws Exception {
        String body = response.getContentAsString();
        // 简易解析 {"code":"A0004",...}
        int idx = body.indexOf("\"" + field + "\"");
        int colon = body.indexOf(':', idx);
        int start = body.indexOf('"', colon) + 1;
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }
}
