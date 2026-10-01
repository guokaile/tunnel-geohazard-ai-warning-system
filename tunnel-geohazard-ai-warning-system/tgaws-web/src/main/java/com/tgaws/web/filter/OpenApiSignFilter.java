package com.tgaws.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tgaws.business.sys.auth.ThirdAppService;
import com.tgaws.common.result.ErrorCode;
import com.tgaws.common.result.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Open API 签名过滤器（T-702，《4》4.4.3）：
 *
 * <ul>
 *   <li>头：X-App-Key / X-Timestamp（毫秒）/ X-Nonce / X-Sign；</li>
 *   <li>签名：HMAC-SHA256(appSecret, timestamp+nonce+METHOD+PATH+body) Hex 小写，
 *       常数时间比对（MessageDigest.isEqual）——<b>验签先于 doFilter</b>，
 *       body 预读缓存后透传下游（CachedBodyRequest）；</li>
 *   <li>防重放：5min 时间窗（A0005）+ nonce 24h 唯一（A0004）；</li>
 *   <li>注册校验：不存在/停用/过期统一 A0004（防枚举）；IP 白名单（空=不限）；</li>
 *   <li>密钥轮换：secret_rotate_time 后 24h 内新旧密钥并行；</li>
 *   <li>限流：单 app 100 请求/分钟 → HTTP 429。</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class OpenApiSignFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(OpenApiSignFilter.class);

    private static final long TIME_WINDOW_MS = 5 * 60 * 1000L;
    private static final int RATE_LIMIT_PER_MIN = 100;

    private final ThirdAppService thirdAppService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** nonce 重放缓存：24h 内唯一 */
    private final Cache<String, Boolean> nonceCache = Caffeine.newBuilder()
            .maximumSize(200_000)
            .expireAfterWrite(Duration.ofHours(24))
            .build();

    /** 单 app 限流计数器：1min 窗口 */
    private final Cache<String, AtomicInteger> rateCache = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(Duration.ofMinutes(1))
            .build();

    public OpenApiSignFilter(ThirdAppService thirdAppService) {
        this.thirdAppService = thirdAppService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/open/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String appKey = request.getHeader("X-App-Key");
        String timestamp = request.getHeader("X-Timestamp");
        String nonce = request.getHeader("X-Nonce");
        String sign = request.getHeader("X-Sign");
        if (isBlank(appKey) || isBlank(timestamp) || isBlank(nonce) || isBlank(sign)) {
            reject(response, HttpServletResponse.SC_OK, ErrorCode.A0004, "缺少签名头");
            return;
        }

        // 时间窗（防重放）
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            reject(response, HttpServletResponse.SC_OK, ErrorCode.A0004, "时间戳格式错误");
            return;
        }
        if (Math.abs(System.currentTimeMillis() - ts) > TIME_WINDOW_MS) {
            reject(response, HttpServletResponse.SC_OK, ErrorCode.A0005, "请求时间超窗（5min）");
            return;
        }

        // nonce 唯一（24h 重放拒绝）
        if (nonceCache.getIfPresent(nonce) != null) {
            reject(response, HttpServletResponse.SC_OK, ErrorCode.A0004, "nonce 重放");
            return;
        }

        // 应用注册（不存在/停用/过期统一拒绝，防枚举）
        ThirdAppService.AppCredential credential = thirdAppService.credential(appKey);
        if (credential == null) {
            reject(response, HttpServletResponse.SC_OK, ErrorCode.A0004, "应用未注册或不可用");
            return;
        }

        // IP 白名单（空=不限）
        if (!credential.ipWhitelist().isEmpty()
                && !credential.ipWhitelist().contains(request.getRemoteAddr())) {
            reject(response, HttpServletResponse.SC_OK, ErrorCode.A0004, "来源 IP 不在白名单");
            return;
        }

        // 限流：100/min/app
        AtomicInteger counter = rateCache.get(appKey, k -> new AtomicInteger());
        if (counter.incrementAndGet() > RATE_LIMIT_PER_MIN) {
            reject(response, 429, ErrorCode.D0005, "请求频率超限");
            return;
        }

        // 预读 body（验签必须；验签后以缓存体透传下游）
        byte[] body = request.getInputStream().readAllBytes();
        String bodyStr = new String(body, StandardCharsets.UTF_8);
        String signBase = ts + nonce + request.getMethod() + request.getRequestURI() + bodyStr;
        boolean matched = constantTimeEquals(hmacSha256Hex(credential.secret(), signBase), sign);
        if (!matched && credential.secretOld() != null) {
            matched = constantTimeEquals(hmacSha256Hex(credential.secretOld(), signBase), sign);
        }
        if (!matched) {
            reject(response, HttpServletResponse.SC_OK, ErrorCode.A0004, "签名无效");
            return;
        }

        // 验签通过才登记 nonce（失败不计入，重试不误伤）
        nonceCache.put(nonce, Boolean.TRUE);
        request.setAttribute("openAppKey", appKey);
        chain.doFilter(new CachedBodyRequest(request, body), response);
    }

    /** 常数时间比对（防时序侧信道） */
    static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    static String hmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 不可用", e);
        }
    }

    private void reject(HttpServletResponse response, int status, ErrorCode code, String detail)
            throws IOException {
        log.warn("Open API 拒绝：{}（{}）", detail, code.getCode());
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), Result.fail(code));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** 缓存体请求包装（验签预读后透传） */
    private static class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // 同步流无需监听器
                }
            };
        }
    }
}
