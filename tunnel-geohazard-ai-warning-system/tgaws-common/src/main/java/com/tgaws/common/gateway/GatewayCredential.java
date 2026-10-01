package com.tgaws.common.gateway;

/**
 * 网关凭据（PSK 解密后 + 启停状态，对应 mon_gateway.secret/status）。
 *
 * <p>契约层位于 common：access（认证消费）与 business（mon_gateway 实现）均可引用，
 * 不破坏"business 不依赖 access"的依赖倒置约束。</p>
 *
 * @param secret  注册凭据 PSK（密文由实现层解密后提供）
 * @param enabled 是否启用（停用网关拒绝注册）
 */
public record GatewayCredential(String secret, boolean enabled) {
}
