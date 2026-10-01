package com.tgaws.business.sys.mapper;

import com.tgaws.business.sys.entity.PermissionEntity;

import java.util.List;

/**
 * 权限点 Mapper（T-813：A18 权限树；权限点种子由 04_init_roles.sql 维护，运行期只读）。
 */
public interface PermissionMapper {

    List<PermissionEntity> selectAll();
}
