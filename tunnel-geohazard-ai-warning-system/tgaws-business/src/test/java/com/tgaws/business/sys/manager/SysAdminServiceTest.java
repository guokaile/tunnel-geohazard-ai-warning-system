package com.tgaws.business.sys.manager;

import com.tgaws.business.sys.entity.PermissionEntity;
import com.tgaws.business.sys.entity.RoleEntity;
import com.tgaws.business.sys.entity.UserEntity;
import com.tgaws.business.sys.mapper.PermissionMapper;
import com.tgaws.business.sys.mapper.RoleMapper;
import com.tgaws.business.sys.mapper.UserCrudMapper;
import com.tgaws.business.sys.mapper.UserMapper;
import com.tgaws.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 系统管理服务单测（T-813：用户/角色守卫与权限重绑）。
 */
class SysAdminServiceTest {

    private UserCrudMapper crudMapper;
    private UserMapper userMapper;
    private RoleMapper roleMapper;
    private PermissionMapper permissionMapper;
    private UserAdminService userAdminService;
    private RoleAdminService roleAdminService;

    @BeforeEach
    void setUp() {
        crudMapper = mock(UserCrudMapper.class);
        userMapper = mock(UserMapper.class);
        roleMapper = mock(RoleMapper.class);
        permissionMapper = mock(PermissionMapper.class);
        userAdminService = new UserAdminService(crudMapper, userMapper);
        roleAdminService = new RoleAdminService(roleMapper, permissionMapper, crudMapper);
    }

    @Test
    void createUserDuplicateUsernameRejected() {
        when(userMapper.selectByUsername("dup")).thenReturn(new UserEntity());
        assertThrows(BizException.class, () -> userAdminService.create("dup", "x", null, List.of()));
        verify(crudMapper, never()).insert(any());
    }

    @Test
    void createUserRebindsRoles() {
        when(userMapper.selectByUsername("u1")).thenReturn(null);
        doAnswer(inv -> {
            UserEntity e = inv.getArgument(0);
            e.setId(42L); // 模拟 useGeneratedKeys 回填
            return 1;
        }).when(crudMapper).insert(any());
        userAdminService.create("u1", "张三", "13800000000", List.of(2L, 3L));
        verify(crudMapper).deleteUserRoles(42L);
        verify(crudMapper).insertUserRole(42L, 2L);
        verify(crudMapper).insertUserRole(42L, 3L);
    }

    @Test
    void deleteSelfRejected() {
        when(userMapper.selectById(9L)).thenReturn(new UserEntity());
        assertThrows(BizException.class, () -> userAdminService.delete(9L, 9L));
        verify(crudMapper, never()).logicDelete(anyLong());
    }

    @Test
    void resetPasswordForcesExpiryState() {
        when(userMapper.selectById(5L)).thenReturn(new UserEntity());
        userAdminService.resetPassword(5L);
        verify(crudMapper).updatePassword(eq(5L), any());
    }

    @Test
    void createRoleDuplicateCodeRejected() {
        when(roleMapper.selectByCode("ADMIN")).thenReturn(new RoleEntity());
        assertThrows(BizException.class, () -> roleAdminService.create("ADMIN", "管理员", null, 1));
    }

    @Test
    void deleteRoleWithUsersRejected() {
        when(roleMapper.selectById(2L)).thenReturn(new RoleEntity());
        when(crudMapper.countUsersByRole(2L)).thenReturn(3L);
        assertThrows(BizException.class, () -> roleAdminService.delete(2L));
        verify(roleMapper, never()).logicDelete(anyLong());
    }

    @Test
    void assignPermissionsRebuilds() {
        when(roleMapper.selectById(2L)).thenReturn(new RoleEntity());
        roleAdminService.assignPermissions(2L, List.of(11L, 13L));
        verify(roleMapper).deleteRolePerms(2L);
        verify(roleMapper).insertRolePerm(2L, 11L);
        verify(roleMapper).insertRolePerm(2L, 13L);
    }

    @Test
    void permissionTreeBuildsHierarchy() {
        PermissionEntity root = new PermissionEntity();
        root.setId(1L);
        root.setPermCode("warn:event:view");
        PermissionEntity child = new PermissionEntity();
        child.setId(2L);
        child.setParentId(1L);
        child.setPermCode("warn:event:close");
        when(permissionMapper.selectAll()).thenReturn(List.of(root, child));
        List<PermissionEntity> tree = roleAdminService.permissionTree();
        assertEquals(1, tree.size());
        assertEquals(1, tree.get(0).getChildren().size());
    }
}
