package com.tgaws.web.security;

import com.tgaws.common.datascope.DataScope;
import com.tgaws.common.datascope.DataScopeHelper;
import com.tgaws.common.exception.BizException;
import com.tgaws.common.result.ErrorCode;

import java.util.Objects;

/**
 * 数据出口守卫（T-701 验收：越权访问他隧道数据 → C0008）。
 * 写/改/删与隧道级查询接口在 Controller 入口校验；查询条件级控制由 Manager
 * 层 DataScopeHelper 显式构建（T-704 ArchUnit 守护）。
 */
public final class ScopeGuard {

    private ScopeGuard() {
    }

    /** 隧道级出口：目标隧道必须在数据范围内（scope=全部放行） */
    public static void requireTunnel(Long tunnelId) {
        if (!DataScopeHelper.checkTunnel(LoginContext.getScope(), tunnelId)) {
            throw new BizException(ErrorCode.C0008);
        }
    }

    /** 本人级出口：仅本人或全部范围放行（巡检填报/处置反馈类） */
    public static void requireSelf(Long targetUserId) {
        DataScope scope = LoginContext.getScope();
        if (scope == null) {
            throw new BizException(ErrorCode.C0008);
        }
        if (scope.getType() == DataScope.TYPE_ALL) {
            return;
        }
        if (scope.getType() == DataScope.TYPE_SELF
                && Objects.equals(scope.getUserId(), targetUserId)) {
            return;
        }
        throw new BizException(ErrorCode.C0008);
    }

    /** 当前登录用户（W7 起 userId 一律取认证上下文，不再信任请求体） */
    public static long userId() {
        LoginContext.LoginUser user = LoginContext.getUser();
        if (user == null) {
            throw new BizException(ErrorCode.C0006);
        }
        return user.userId();
    }

    /** 当前登录用户名（留痕用） */
    public static String username() {
        LoginContext.LoginUser user = LoginContext.getUser();
        return user == null ? "" : user.username();
    }
}
