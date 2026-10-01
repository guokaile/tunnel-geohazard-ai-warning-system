package com.tgaws.web.controller;

import com.tgaws.business.sys.entity.PermissionEntity;
import com.tgaws.business.sys.entity.RoleEntity;
import com.tgaws.business.sys.entity.UserEntity;
import com.tgaws.business.sys.manager.LogQueryService;
import com.tgaws.business.sys.manager.RoleAdminService;
import com.tgaws.business.sys.manager.UserAdminService;
import com.tgaws.common.result.PageResult;
import com.tgaws.common.result.Result;
import com.tgaws.web.log.OperLog;
import com.tgaws.web.security.LoginContext;
import com.tgaws.web.security.RateLimit;
import com.tgaws.web.security.RequirePermission;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 系统管理接口（《4》API-A06~A23，T-813——F5 系统管理后端支撑）。
 * 字典（A19~A21）与通知通道（A24~A26）前端本期未对接，暂缓。
 */
@RestController
@RequestMapping("/api/v1/sys")
public class SysController {

    private static final int MAX_PAGE_SIZE = 200;

    private final UserAdminService userAdminService;
    private final RoleAdminService roleAdminService;
    private final LogQueryService logQueryService;

    public SysController(UserAdminService userAdminService, RoleAdminService roleAdminService,
                         LogQueryService logQueryService) {
        this.userAdminService = userAdminService;
        this.roleAdminService = roleAdminService;
        this.logQueryService = logQueryService;
    }

    // ==================== 用户管理（A06~A11） ====================

    /** A06 用户分页 */
    @GetMapping("/users")
    @RequirePermission("sys:user:view")
    @RateLimit(RateLimit.Tier.QUERY)
    public Result<PageResult<UserEntity>> users(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Integer status,
                                                @RequestParam(required = false) Long roleId,
                                                @RequestParam(defaultValue = "1") int pageNum,
                                                @RequestParam(defaultValue = "20") int pageSize) {
        pageNum = Math.max(pageNum, 1);
        pageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        return Result.ok(userAdminService.page(keyword, status, roleId, pageNum, pageSize));
    }

    /** A07 新增用户 */
    @PostMapping("/users")
    @RequirePermission("sys:user:edit")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "新增用户", module = "系统管理")
    public Result<Long> createUser(@RequestBody Map<String, Object> body) {
        Long id = userAdminService.create(str(body.get("username")), str(body.get("realName")),
                str(body.get("phone")), longList(body.get("roleIds")));
        return Result.ok(id);
    }

    /** A08 修改用户 */
    @PutMapping("/users/{id}")
    @RequirePermission("sys:user:edit")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "修改用户", module = "系统管理")
    public Result<Void> updateUser(@PathVariable long id, @RequestBody Map<String, Object> body) {
        userAdminService.update(id, str(body.get("realName")), str(body.get("phone")),
                longList(body.get("roleIds")));
        return Result.ok();
    }

    /** A09 删除用户（逻辑删；不能删除自己） */
    @DeleteMapping("/users/{id}")
    @RequirePermission("sys:user:edit")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "删除用户", module = "系统管理")
    public Result<Void> deleteUser(@PathVariable long id) {
        userAdminService.delete(id, LoginContext.getUser().userId());
        return Result.ok();
    }

    /** A10 启停用户 */
    @PutMapping("/users/{id}/status")
    @RequirePermission("sys:user:edit")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "启停用户", module = "系统管理")
    public Result<Void> updateUserStatus(@PathVariable long id, @RequestBody Map<String, Object> body) {
        userAdminService.updateStatus(id, intOf(body.get("status"), 1));
        return Result.ok();
    }

    /** A11 重置密码 */
    @PostMapping("/users/{id}/reset-password")
    @RequirePermission("sys:user:edit")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "重置密码", module = "系统管理")
    public Result<Void> resetPassword(@PathVariable long id) {
        userAdminService.resetPassword(id);
        return Result.ok();
    }

    // ==================== 角色与权限（A12~A18） ====================

    /** A12 角色列表 */
    @GetMapping("/roles")
    @RequirePermission("sys:role:view")
    @RateLimit(RateLimit.Tier.QUERY)
    public Result<List<RoleEntity>> roles(@RequestParam(required = false) String keyword) {
        return Result.ok(roleAdminService.list(keyword));
    }

    /** A13 新增角色 */
    @PostMapping("/roles")
    @RequirePermission("sys:role:edit")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "新增角色", module = "系统管理")
    public Result<Long> createRole(@RequestBody Map<String, Object> body) {
        return Result.ok(roleAdminService.create(str(body.get("roleCode")), str(body.get("roleName")),
                str(body.get("remark")), intOrNull(body.get("dataScope"))));
    }

    /** A14 修改角色 */
    @PutMapping("/roles/{id}")
    @RequirePermission("sys:role:edit")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "修改角色", module = "系统管理")
    public Result<Void> updateRole(@PathVariable long id, @RequestBody Map<String, Object> body) {
        roleAdminService.update(id, str(body.get("roleName")), str(body.get("remark")),
                intOrNull(body.get("dataScope")));
        return Result.ok();
    }

    /** A15 删除角色（无用户占用） */
    @DeleteMapping("/roles/{id}")
    @RequirePermission("sys:role:edit")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "删除角色", module = "系统管理")
    public Result<Void> deleteRole(@PathVariable long id) {
        roleAdminService.delete(id);
        return Result.ok();
    }

    /** A16 角色权限点 */
    @GetMapping("/roles/{id}/permissions")
    @RequirePermission("sys:role:view")
    @RateLimit(RateLimit.Tier.QUERY)
    public Result<List<Long>> rolePermissions(@PathVariable long id) {
        return Result.ok(roleAdminService.permissions(id));
    }

    /** A17 分配权限 */
    @PutMapping("/roles/{id}/permissions")
    @RequirePermission("sys:role:edit")
    @RateLimit(RateLimit.Tier.WRITE)
    @OperLog(value = "分配权限", module = "系统管理")
    public Result<Void> assignPermissions(@PathVariable long id, @RequestBody Map<String, Object> body) {
        roleAdminService.assignPermissions(id, longList(body.get("permIds")));
        return Result.ok();
    }

    /** A18 权限树 */
    @GetMapping("/permissions")
    @RequirePermission("sys:role:view")
    @RateLimit(RateLimit.Tier.QUERY)
    public Result<List<PermissionEntity>> permissions() {
        return Result.ok(roleAdminService.permissionTree());
    }

    // ==================== 审计日志（A22~A23） ====================

    /** A22 操作日志分页 */
    @GetMapping("/logs/oper")
    @RequirePermission("sys:log:view")
    @RateLimit(RateLimit.Tier.QUERY)
    public Result<PageResult<Map<String, Object>>> operLogs(@RequestParam(required = false) String username,
                                                            @RequestParam(required = false) String module,
                                                            @RequestParam(required = false) String from,
                                                            @RequestParam(required = false) String to,
                                                            @RequestParam(defaultValue = "1") int pageNum,
                                                            @RequestParam(defaultValue = "20") int pageSize) {
        pageNum = Math.max(pageNum, 1);
        pageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        return Result.ok(logQueryService.operPage(username, module, parse(from), parse(to), pageNum, pageSize));
    }

    /** A23 登录日志分页 */
    @GetMapping("/logs/login")
    @RequirePermission("sys:log:view")
    @RateLimit(RateLimit.Tier.QUERY)
    public Result<PageResult<Map<String, Object>>> loginLogs(@RequestParam(required = false) String username,
                                                             @RequestParam(required = false) String from,
                                                             @RequestParam(required = false) String to,
                                                             @RequestParam(defaultValue = "1") int pageNum,
                                                             @RequestParam(defaultValue = "20") int pageSize) {
        pageNum = Math.max(pageNum, 1);
        pageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        return Result.ok(logQueryService.loginPage(username, parse(from), parse(to), pageNum, pageSize));
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    @SuppressWarnings("unchecked")
    private static List<Long> longList(Object o) {
        if (o instanceof List<?> list) {
            return list.stream().map(v -> Long.valueOf(String.valueOf(v))).toList();
        }
        return List.of();
    }

    private static Integer intOf(Object o, int def) {
        return o == null ? def : Integer.valueOf(String.valueOf(o));
    }

    private static Integer intOrNull(Object o) {
        return o == null ? null : Integer.valueOf(String.valueOf(o));
    }

    private static LocalDateTime parse(String s) {
        return s == null || s.isEmpty() ? null : LocalDateTime.parse(s);
    }
}
