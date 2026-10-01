package com.tgaws.web.controller;

import com.tgaws.business.sys.auth.AuthService;
import com.tgaws.common.result.Result;
import com.tgaws.web.log.OperLog;
import com.tgaws.web.security.LoginContext;
import com.tgaws.web.security.RateLimit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证接口（《4》API-A01~A03：登录/登出/刷新轮换）。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** API-A01 登录 */
    @PostMapping("/login")
    @RateLimit(RateLimit.Tier.LOGIN)
    public Result<Map<String, Object>> login(@RequestBody LoginRequest request,
                                             @RequestHeader(value = "X-Forwarded-For", required = false) String forwarded) {
        AuthService.LoginResult result = authService.login(request.username(), request.password(),
                forwarded == null ? "127.0.0.1" : forwarded.split(",")[0].trim());
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", result.userId());
        user.put("username", result.username());
        user.put("realName", result.realName());
        user.put("phone", result.phone());
        return Result.ok(Map.of(
                "token", result.accessToken(),
                "refreshToken", result.refreshToken(),
                "expiresIn", result.accessExpiresInSec(),
                "user", user));
    }

    /** API-A02 登出 */
    @PostMapping("/logout")
    @OperLog(value = "用户登出", module = "认证")
    public Result<Void> logout(@RequestBody TokenRequest request) {
        authService.logout(request.accessToken(), request.refreshToken());
        return Result.ok();
    }

    /** API-A03 刷新（轮换） */
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@RequestBody Map<String, String> body,
                                               @RequestHeader(value = "X-Forwarded-For", required = false) String forwarded) {
        String refreshToken = body.get("refreshToken");
        AuthService.LoginResult result = authService.refresh(refreshToken,
                forwarded == null ? "127.0.0.1" : forwarded.split(",")[0].trim());
        return Result.ok(Map.of(
                "token", result.accessToken(),
                "refreshToken", result.refreshToken(),
                "expiresIn", result.accessExpiresInSec()));
    }

    /** API-A04 当前用户信息（user+角色+权限点，前端按钮显隐数据源） */
    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        AuthService.MeResult me = authService.me(LoginContext.getUser().userId());
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", me.user().getId());
        user.put("username", me.user().getUsername());
        user.put("realName", me.user().getRealName());
        user.put("phone", me.user().getPhone());
        user.put("status", me.user().getStatus());
        return Result.ok(Map.of(
                "user", user,
                "roles", me.roles(),
                "permissions", me.permissions()));
    }

    /** API-A05 修改本人密码（原密码校验+复杂度） */
    @PutMapping("/password")
    @OperLog(value = "修改密码", module = "认证")
    public Result<Void> changePassword(@RequestBody Map<String, String> body) {
        authService.changePassword(LoginContext.getUser().userId(),
                body.get("oldPassword"), body.get("newPassword"));
        return Result.ok();
    }

    /** 登录请求 */
    public record LoginRequest(String username, String password) {
    }

    /** 令牌请求（登出用） */
    public record TokenRequest(String accessToken, String refreshToken) {
    }
}
