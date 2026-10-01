package com.tgaws.business.sys.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与校验（《4》4.2/4.8：access 30min / refresh 2h 滑动+轮换，HS256）。
 *
 * <p>密钥来源环境变量 JWT_SECRET（SHA-256 派生 32 字节，任意长度口令安全）；
 * 未配置时抛异常（拒绝明文降级，与 CryptoUtil 同口径）。</p>
 */
@Component
public class JwtTokenProvider {

    /** access 有效期（秒）：30min */
    public static final long ACCESS_TTL_SECONDS = 30L * 60L;

    /** refresh 有效期（秒）：2h 滑动（每次轮换重签，滑动续期） */
    public static final long REFRESH_TTL_SECONDS = 2L * 3600L;

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_USERNAME = "uname";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;

    public JwtTokenProvider(@Value("${jwt.secret:}") String secret) {
        this.key = deriveKey(secret);
    }

    public String issueAccessToken(long userId, String username) {
        return issue(userId, username, TYPE_ACCESS, ACCESS_TTL_SECONDS);
    }

    public String issueRefreshToken(long userId, String username) {
        return issue(userId, username, TYPE_REFRESH, REFRESH_TTL_SECONDS);
    }

    /** 解析并校验（过期/篡改抛 JwtException）；类型不符返回 null */
    public ParsedToken parse(String token, String expectType) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            if (!expectType.equals(claims.get(CLAIM_TYPE, String.class))) {
                return null;
            }
            return new ParsedToken(
                    Long.parseLong(claims.getSubject()),
                    claims.get(CLAIM_USERNAME, String.class),
                    claims.getId(),
                    claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    private String issue(long userId, String username, String type, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_TYPE, type)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    private static SecretKey deriveKey(String secret) {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalStateException("环境变量 JWT_SECRET 未配置（拒绝明文降级）");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(digest);
        } catch (Exception e) {
            throw new IllegalStateException("JWT 密钥派生失败", e);
        }
    }

    /**
     * 解析结果。
     *
     * @param userId   用户 id
     * @param username 登录名
     * @param jti      token 唯一标识（黑名单/轮换依据）
     * @param expiresAt 过期时刻
     */
    public record ParsedToken(long userId, String username, String jti, Instant expiresAt) {
    }
}
