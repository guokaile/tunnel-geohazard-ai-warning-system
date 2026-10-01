package com.tgaws.business.sys.mapper;

import com.tgaws.business.sys.entity.PermissionEntity;
import com.tgaws.business.sys.entity.RoleEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色管理 Mapper（T-813：A12~A17 sys_role / sys_role_permission）。
 */
public interface RoleMapper {

    List<RoleEntity> selectList(@Param("keyword") String keyword);

    RoleEntity selectById(@Param("id") long id);

    RoleEntity selectByCode(@Param("roleCode") String roleCode);

    int insert(RoleEntity entity);

    int update(RoleEntity entity);

    int logicDelete(@Param("id") long id);

    List<Long> selectPermIdsByRole(@Param("roleId") long roleId);

    int deleteRolePerms(@Param("roleId") long roleId);

    int insertRolePerm(@Param("roleId") long roleId, @Param("permId") long permId);
}
