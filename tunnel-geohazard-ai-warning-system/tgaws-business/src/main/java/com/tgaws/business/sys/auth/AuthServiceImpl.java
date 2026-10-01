package com.tgaws.business.sys.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tgaws.business.sys.entity.UserEntity;
import com.tgaws.business.sys.mapper.UserMapper;
import com.tgaws.business.sys.token.JwtTokenProvider;
import com.tgaws.common.exception.BizException;
import com.tgaws.common.result.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 认证服务实现（《4》4.2/4.8 三项定稿约束，评审 4.1）：
 *
 * <ul>
 *   <li><b>轮换</b>：refresh jti 记录在 Caffeine（TTL 2h 滑动），刷新校验旧 jti 后
 *       **立即逐出并签发新 jti**——旧 refresh 即刻失效，多终端各自独立互不挤掉；</li>
 *   <li><b>登出黑名单</b>：access jti 进 Caffeine 黑名单，**TTL=30min ≥ access 有效期**
 *      （容量 10000）；登出同时逐出 refresh 记录；</li>
 *   <li><b>防枚举</b>：用户不存在时对**固定假哈希**执行 BCrypt（耗时与真校验同阶），
 *       与口令错误返回同一 C0002，消除响应时序差异（8.8 S-02）；</li>
 *   <li><b>锁定</b>：登录失败计数（username+ip 维度，TTL 30min），第 5 次失败直接 C0003。</li>
 * </ul>
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private static final ZoneId DB_ZONE = ZoneId.of("Asia/Shanghai");

    /** 锁定失败次数（第 5 次失败即锁定） */
    private static final int MAX_FAIL_COUNT = 5;

    /** 锁定/失败计数窗口（分钟） */
    private static final int LOCK_MINUTES = 30;

    /** 密码有效期（天） */
    private static final int PWD_EXPIRE_DAYS = 90;

    /** 防枚举假哈希（与真实 BCrypt 同 cost，保证耗时同阶） */
    private static final String DUMMY_HASH =
            "$2b$10$IypV1Qq2d.9VSRC2bL.Tru3Uo1kpNx0DEM2BWgeYZev2E/J/HtQWm";

    private final UserMapper userMapper;
    private final JwtTokenProvider tokenProvider;
    private final UserRoleService userRoleService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** refresh 轮换记录：jti → userId（TTL 2h 滑动；逐出=旧 refresh 失效） */
    private final Cache<String, Long> refreshRegistry = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(JwtTokenProvider.REFRESH_TTL_SECONDS))
            .build();

    /** access 登出黑名单：jti → 1（TTL 30min ≥ access 有效期） */
    private final Cache<String, Boolean> accessBlacklist = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(JwtTokenProvider.ACCESS_TTL_SECONDS))
            .build();

    /** 登录失败计数：username#ip → count（防爆破） */
    private final Cache<String, Integer> failCounter = Caffeine.newBuilder()
            .maximumSize(20_000)
            .expireAfterWrite(Duration.ofMinutes(LOCK_MINUTES))
            .build();

    public AuthServiceImpl(UserMapper userMapper, JwtTokenProvider tokenProvider,
                          UserRoleService userRoleService) {
        this.userMapper = userMapper;
        this.tokenProvider = tokenProvider;
        this.userRoleService = userRoleService;
    }

    @Override
    public MeResult me(long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.C0006);
        }
        UserRoleService.Authorities authorities = userRoleService.authorities(userId);
        return new MeResult(user, authorities.roles(), authorities.permissions());
    }

    @Override
    public void changePassword(long userId, String oldPassword, String newPassword) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.C0006);
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BizException(ErrorCode.A0018);
        }
        if (newPassword == null || newPassword.length() < 8
                || !newPassword.matches(".*[A-Za-z].*") || !newPassword.matches(".*[0-9].*")) {
            throw new BizException(ErrorCode.A0002, "新密码至少 8 位且包含字母与数字");
        }
        userMapper.updatePassword(userId, passwordEncoder.encode(newPassword));
    }

    @Override
    public LoginResult login(String username, String password, String ip) {
        String failKey = username + "#" + (ip == null ? "" : ip);
        Integer fails = failCounter.getIfPresent(failKey);
        if (fails != null && fails >= MAX_FAIL_COUNT) {
            log.warn("账号已锁定：{}（{} 分钟内）", username, LOCK_MINUTES);
            throw new BizException(ErrorCode.C0003);
        }
        UserEntity user = userMapper.selectByUsername(username);
        if (user == null) {
            // 防枚举：假哈希校验保持与真校验同阶耗时
            passwordEncoder.matches(password, DUMMY_HASH);
            recordFail(username, ip, failKey, null, "用户名或密码错误");
            throw new BizException(ErrorCode.C0002);
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            recordFail(username, ip, failKey, user.getId(), "用户名或密码错误");
            // 第 5 次失败：直接返回锁定
            int count = failCounter.get(failKey, k -> 0);
            if (count >= MAX_FAIL_COUNT) {
                throw new BizException(ErrorCode.C0003);
            }
            throw new BizException(ErrorCode.C0002);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ErrorCode.C0004);
        }
        if (user.getPwdUpdateTime() != null && user.getPwdUpdateTime()
                .isBefore(LocalDateTime.now(DB_ZONE).minusDays(PWD_EXPIRE_DAYS))) {
            throw new BizException(ErrorCode.C0005);
        }
        failCounter.invalidate(failKey);
        userMapper.updateLoginInfo(user.getId(), ip, LocalDateTime.now(DB_ZONE));
        userMapper.insertLoginLog(user.getId(), username, 1, 1, null, ip);
        return issuePair(user, ip);
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        JwtTokenProvider.ParsedToken access = tokenProvider.parse(accessToken, "access");
        if (access != null) {
            accessBlacklist.put(access.jti(), Boolean.TRUE);
        }
        JwtTokenProvider.ParsedToken refresh = tokenProvider.parse(refreshToken, "refresh");
        if (refresh != null) {
            refreshRegistry.invalidate(refresh.jti());
        }
    }

    @Override
    public LoginResult refresh(String refreshToken, String ip) {
        JwtTokenProvider.ParsedToken parsed = tokenProvider.parse(refreshToken, "refresh");
        if (parsed == null) {
            throw new BizException(ErrorCode.C0007);
        }
        // 轮换：旧 jti 必须仍在注册表，校验通过立即逐出（旧 refresh 即刻失效）
        Long userId = refreshRegistry.getIfPresent(parsed.jti());
        if (userId == null || userId != parsed.userId()) {
            log.warn("refresh 轮换校验失败：jti 已失效或已使用（防重放）");
            throw new BizException(ErrorCode.C0007);
        }
        refreshRegistry.invalidate(parsed.jti());
        UserEntity user = userMapper.selectByUsername(parsed.username());
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ErrorCode.C0004);
        }
        return issuePair(user, ip);
    }

    @Override
    public JwtTokenProvider.ParsedToken verifyAccess(String accessToken) {
        JwtTokenProvider.ParsedToken parsed = tokenProvider.parse(accessToken, "access");
        if (parsed == null) {
            return null;
        }
        if (Boolean.TRUE.equals(accessBlacklist.getIfPresent(parsed.jti()))) {
            return null; // 登出黑名单命中
        }
        return parsed;
    }

    private LoginResult issuePair(UserEntity user, String ip) {
        String access = tokenProvider.issueAccessToken(user.getId(), user.getUsername());
        String refresh = tokenProvider.issueRefreshToken(user.getId(), user.getUsername());
        refreshRegistry.put(tokenProvider.parse(refresh, "refresh").jti(), user.getId());
        return new LoginResult(access, refresh,
                JwtTokenProvider.ACCESS_TTL_SECONDS, JwtTokenProvider.REFRESH_TTL_SECONDS,
                user.getId(), user.getUsername(), user.getRealName(), user.getPhone());
    }

    private void recordFail(String username, String ip, String failKey,
                            Long userId, String reason) {
        int count = failCounter.get(failKey, k -> 0) + 1;
        failCounter.put(failKey, count);
        userMapper.insertLoginLog(userId, username, 1, 0, reason, ip);
    }
}
