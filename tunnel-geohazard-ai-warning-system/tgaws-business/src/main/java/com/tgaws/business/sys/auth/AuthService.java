package com.tgaws.business.sys.auth;

import com.tgaws.business.sys.entity.UserEntity;
import com.tgaws.business.sys.token.JwtTokenProvider;

/**
 * 认证服务契约（登录/登出/刷新轮换）。
 */
public interface AuthService {

    /** 登录（防枚举：不存在与口令错误统一 C0002；锁定 C0003；停用 C0004；过期 C0005） */
    LoginResult login(String username, String password, String ip);

    /** 登出（access 进黑名单 + refresh 轮换记录作废） */
    void logout(String accessToken, String refreshToken);

    /** 刷新（轮换：旧 refresh 立即失效，发新 token 对；滑动续期 2h） */
    LoginResult refresh(String refreshToken, String ip);

    /** 解析 access token（拦截器用；黑名单命中返回 null） */
    JwtTokenProvider.ParsedToken verifyAccess(String accessToken);

    /** A04 当前用户信息（user + 角色 + 权限点；userId 取认证上下文） */
    MeResult me(long userId);

    /** A05 修改本人密码（校验原密码 + 复杂度；成功后旧 access 建议重登，本期仅刷新 pwd 状态） */
    void changePassword(long userId, String oldPassword, String newPassword);

    record MeResult(UserEntity user, java.util.Set<String> roles, java.util.Set<String> permissions) {
    }

    /** 登录结果 */
    record LoginResult(String accessToken, String refreshToken, long accessExpiresInSec,
                       long refreshExpiresInSec, long userId, String username,
                       String realName, String phone) {
    }
}
