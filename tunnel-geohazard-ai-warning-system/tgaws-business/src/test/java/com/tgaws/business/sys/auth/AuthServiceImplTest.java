package com.tgaws.business.sys.auth;

import com.tgaws.business.sys.entity.UserEntity;
import com.tgaws.business.sys.mapper.UserMapper;
import com.tgaws.business.sys.auth.UserRoleService;
import com.tgaws.business.sys.token.JwtTokenProvider;
import com.tgaws.common.exception.BizException;
import com.tgaws.common.result.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 认证服务单测（评审 4.1 三项约束：轮换旧 refresh 立即失效 / 黑名单 TTL≥access /
 * 防枚举同码同耗时——不存在与口令错误统一 C0002 且都执行 BCrypt）。
 */
class AuthServiceImplTest {

    private static final String PASSWORD = "Test@123456";
    private static final String HASH = new BCryptPasswordEncoder().encode(PASSWORD);

    private UserMapper userMapper;
    private UserRoleService userRoleService;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        userRoleService = mock(UserRoleService.class);
        authService = new AuthServiceImpl(userMapper,
                new JwtTokenProvider("unit-test-jwt-secret"), userRoleService);
    }

    private UserEntity activeUser() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword(HASH);
        user.setRealName("管理员");
        user.setStatus(1);
        user.setPwdUpdateTime(LocalDateTime.now().minusDays(1));
        return user;
    }

    @Test
    void loginSuccessReturnsTokenPair() {
        when(userMapper.selectByUsername("admin")).thenReturn(activeUser());
        AuthService.LoginResult result = authService.login("admin", PASSWORD, "127.0.0.1");
        assertNotNull(result.accessToken());
        assertNotNull(result.refreshToken());
        assertEquals(1800, result.accessExpiresInSec(), "access 30min");
        assertEquals(7200, result.refreshExpiresInSec(), "refresh 2h");
        assertNotNull(authService.verifyAccess(result.accessToken()), "access 可校验通过");
    }

    @Test
    void missingUserAndWrongPasswordSameErrorCode() {
        when(userMapper.selectByUsername("ghost")).thenReturn(null);
        BizException missing = assertThrows(BizException.class,
                () -> authService.login("ghost", "whatever", "127.0.0.1"));
        when(userMapper.selectByUsername("admin")).thenReturn(activeUser());
        BizException wrong = assertThrows(BizException.class,
                () -> authService.login("admin", "wrong-password", "127.0.0.1"));
        assertEquals(missing.getErrorCode(), wrong.getErrorCode(), "防枚举：统一 C0002");
        assertEquals(ErrorCode.C0002, missing.getErrorCode());
        // 两分支均执行 BCrypt（假哈希）保持耗时同阶
        Mockito.verify(userMapper, Mockito.times(2))
                .insertLoginLog(any(), anyString(), eq(1), eq(0), anyString(), anyString());
    }

    @Test
    void fifthFailureReturnsLocked() {
        when(userMapper.selectByUsername("admin")).thenReturn(activeUser());
        for (int i = 1; i <= 4; i++) {
            assertThrows(BizException.class,
                    () -> authService.login("admin", "wrong", "127.0.0.1"));
        }
        BizException fifth = assertThrows(BizException.class,
                () -> authService.login("admin", "wrong", "127.0.0.1"));
        assertEquals(ErrorCode.C0003, fifth.getErrorCode(), "第 5 次失败直接锁定");
    }

    @Test
    void disabledUserRejected() {
        UserEntity user = activeUser();
        user.setStatus(0);
        when(userMapper.selectByUsername("admin")).thenReturn(user);
        assertEquals(ErrorCode.C0004, assertThrows(BizException.class,
                () -> authService.login("admin", PASSWORD, "127.0.0.1")).getErrorCode());
    }

    @Test
    void expiredPasswordRejected() {
        UserEntity user = activeUser();
        user.setPwdUpdateTime(LocalDateTime.now().minusDays(91));
        when(userMapper.selectByUsername("admin")).thenReturn(user);
        assertEquals(ErrorCode.C0005, assertThrows(BizException.class,
                () -> authService.login("admin", PASSWORD, "127.0.0.1")).getErrorCode());
    }

    @Test
    void refreshRotationInvalidatesOldRefreshImmediately() {
        when(userMapper.selectByUsername("admin")).thenReturn(activeUser());
        AuthService.LoginResult first = authService.login("admin", PASSWORD, "127.0.0.1");
        // 首次轮换成功
        AuthService.LoginResult rotated = authService.refresh(first.refreshToken(), "127.0.0.1");
        assertNotNull(rotated.refreshToken());
        // 旧 refresh 立即失效（防重放）
        assertEquals(ErrorCode.C0007, assertThrows(BizException.class,
                () -> authService.refresh(first.refreshToken(), "127.0.0.1")).getErrorCode());
    }

    @Test
    void logoutBlacklistsAccessToken() {
        when(userMapper.selectByUsername("admin")).thenReturn(activeUser());
        AuthService.LoginResult result = authService.login("admin", PASSWORD, "127.0.0.1");
        authService.logout(result.accessToken(), result.refreshToken());
        assertNull(authService.verifyAccess(result.accessToken()), "登出后 access 黑名单命中");
        // refresh 亦作废
        assertEquals(ErrorCode.C0007, assertThrows(BizException.class,
                () -> authService.refresh(result.refreshToken(), "127.0.0.1")).getErrorCode());
    }

    @Test
    void multiTerminalRefreshIndependent() {
        when(userMapper.selectByUsername("admin")).thenReturn(activeUser());
        AuthService.LoginResult t1 = authService.login("admin", PASSWORD, "1.1.1.1");
        AuthService.LoginResult t2 = authService.login("admin", PASSWORD, "2.2.2.2");
        // 各自轮换互不挤掉
        assertNotNull(authService.refresh(t1.refreshToken(), "1.1.1.1").accessToken());
        assertNotNull(authService.refresh(t2.refreshToken(), "2.2.2.2").accessToken());
    }
}
