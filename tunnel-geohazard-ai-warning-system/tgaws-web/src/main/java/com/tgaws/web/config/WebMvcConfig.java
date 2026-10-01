package com.tgaws.web.config;

import com.tgaws.web.security.AuthInterceptor;
import com.tgaws.web.security.DataScopeInterceptor;
import com.tgaws.web.security.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 拦截器装配（T-701/T-703）：AuthInterceptor（JWT+权限点）→ DataScopeInterceptor
 * （登录态→DataScope）→ RateLimitInterceptor（接口分级限流，429）。
 * 白名单：认证接口与错误页；其余全部接口（含 SSE）均需 Bearer 认证。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final DataScopeInterceptor dataScopeInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor, DataScopeInterceptor dataScopeInterceptor,
                        RateLimitInterceptor rateLimitInterceptor) {
        this.authInterceptor = authInterceptor;
        this.dataScopeInterceptor = dataScopeInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        // /open/** 由 T-702 OpenApiSignVerifier（HMAC 签名）接管，不走 JWT
        // W8：/api/v1/system/health 免认证（安装向导验证/探活），其余全部接口（含 SSE）均需 Bearer 认证
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/v1/auth/login", "/api/v1/auth/refresh",
                        "/api/v1/system/health", "/error");
        registry.addInterceptor(dataScopeInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/v1/auth/login", "/api/v1/auth/refresh",
                        "/api/v1/system/health", "/error");
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/v1/system/health");
    }
}
