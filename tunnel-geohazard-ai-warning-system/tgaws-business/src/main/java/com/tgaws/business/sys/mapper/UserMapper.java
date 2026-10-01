package com.tgaws.business.sys.mapper;

import com.tgaws.business.sys.entity.UserEntity;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 用户 Mapper（sys_user 认证查询 + T-701 权限/数据范围装载）。
 */
public interface UserMapper {

    /** 按登录名查用户（防枚举：结果对外统一 C0002） */
    UserEntity selectByUsername(@Param("username") String username);

    /** 按主键查（含 password 哈希，仅内部服务用） */
    UserEntity selectById(@Param("id") long id);

    /** 更新密码哈希 + pwd_update_time=NOW()（A05 改密/A11 重置共用） */
    int updatePassword(@Param("id") long id, @Param("password") String password);

    /** 管理员口令注入：仅当 pwd_update_time 仍为 1970-01-01 占位态时更新（幂等） */
    int updateAdminPasswordIfUntouched(@Param("password") String password);

    /** 更新最近登录信息 */
    int updateLoginInfo(@Param("id") long id, @Param("ip") String ip,
                        @Param("loginTime") LocalDateTime loginTime);

    /** 插入登录日志（sys_login_log，仅追加） */
    int insertLoginLog(@Param("userId") Long userId, @Param("username") String username,
                       @Param("loginType") int loginType, @Param("result") int result,
                       @Param("failReason") String failReason, @Param("ip") String ip);

    // ---------- T-701 授权装载 ----------

    /** 权限点集合（sys_user_role → sys_role_permission → sys_permission） */
    Set<String> selectPermissionsByUser(@Param("userId") long userId);

    /** 角色编码集合 */
    Set<String> selectRoleCodesByUser(@Param("userId") long userId);

    /** 多隧道授权（sys_user_role → sys_role_tunnel） */
    Set<Long> selectTunnelIdsByUser(@Param("userId") long userId);

    /** 最大数据范围（1全部 2本隧道 3本断面 4仅本人；无角色返回 null） */
    Integer selectMaxDataScopeByUser(@Param("userId") long userId);
}
