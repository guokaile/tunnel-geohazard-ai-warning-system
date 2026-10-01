package com.tgaws.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tgaws.business.sys.config.SysConfigService;
import com.tgaws.common.result.ErrorCode;
import com.tgaws.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 接口分级限流拦截器（T-703，《6》6.8）：
 *
 * <ul>
 *   <li>分级默认：DASHBOARD 10/s、QUERY 5/s、WRITE 2/s、LOGIN 5/min；
 *       sys_config 键 rate.limit.{tier} 可覆盖（60s 缓存）；</li>
 *   <li>维度：已登录按 userId，未登录按 IP（登录接口场景）；</li>
 *   <li>超限 HTTP 429 + Result（D0005"请求频率超限"口径），计数不进审计。</li>
 * </ul>
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private static final Map<RateLimit.Tier, int[]> DEFAULTS = Map.of(
            RateLimit.Tier.DASHBOARD, new int[]{10, 1},
            RateLimit.Tier.QUERY, new int[]{5, 1},
            RateLimit.Tier.WRITE, new int[]{2, 1},
            RateLimit.Tier.LOGIN, new int[]{5, 60});

    private final SysConfigService sysConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 计数器缓存：key -> 窗口内计数；窗口按级过期 */
    private final Map<RateLimit.Tier, Cache<String, AtomicInteger>> counters = new ConcurrentHashMap<>();

    public RateLimitInterceptor(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
        for (RateLimit.Tier tier : RateLimit.Tier.values()) {
            int window = windowSeconds(tier);
            counters.put(tier, Caffeine.newBuilder()
                    .maximumSize(100_000)
                    .expireAfterWrite(Duration.ofSeconds(window))
                    .build());
        }
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return true;
        }
        RateLimit.Tier tier = rateLimit.value();
        int limit = sysConfigService.getInt("rate.limit." + tier.name().toLowerCase(), defaultLimit(tier));
        if (limit < 1) {
            limit = defaultLimit(tier); // 配置非法（≤0）回退分级默认，防误伤全拒
        }
        LoginContext.LoginUser user = LoginContext.getUser();
        String key = (user != null ? "u" + user.userId() : "ip" + request.getRemoteAddr())
                + ":" + tier.name();
        AtomicInteger counter = counters.get(tier).get(key, k -> new AtomicInteger());
        if (counter.incrementAndGet() > limit) {
            log.warn("限流命中：tier={} key={} limit={}", tier, key, limit);
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            objectMapper.writeValue(response.getWriter(), Result.fail(ErrorCode.D0005, "请求频率超限"));
            return false;
        }
        return true;
    }

    static int defaultLimit(RateLimit.Tier tier) {
        return DEFAULTS.get(tier)[0];
    }

    static int windowSeconds(RateLimit.Tier tier) {
        return DEFAULTS.get(tier)[1];
    }
}
