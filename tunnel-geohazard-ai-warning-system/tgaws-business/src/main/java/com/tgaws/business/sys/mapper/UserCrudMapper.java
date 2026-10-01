package com.tgaws.business.sys.mapper;

import com.tgaws.business.sys.entity.UserEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户管理 Mapper（T-813：A06~A11 sys_user CRUD；列表不查 password 哈希）。
 */
public interface UserCrudMapper {

    List<UserEntity> selectPage(@Param("keyword") String keyword, @Param("status") Integer status,
                                @Param("roleId") Long roleId,
                                @Param("offset") int offset, @Param("limit") int limit);

    long countPage(@Param("keyword") String keyword, @Param("status") Integer status,
                   @Param("roleId") Long roleId);

    int insert(UserEntity entity);

    /** 更新：只动 realName/phone（username 不可改，密码走 updatePassword） */
    int update(UserEntity entity);

    int updateStatus(@Param("id") long id, @Param("status") int status);

    int updatePassword(@Param("id") long id, @Param("password") String password);

    int logicDelete(@Param("id") long id);

    /** 角色占用计数（A15 删除角色守卫） */
    long countUsersByRole(@Param("roleId") long roleId);

    /** 重置换绑角色（A08：先删后插） */
    int deleteUserRoles(@Param("userId") long userId);

    int insertUserRole(@Param("userId") long userId, @Param("roleId") long roleId);
}
