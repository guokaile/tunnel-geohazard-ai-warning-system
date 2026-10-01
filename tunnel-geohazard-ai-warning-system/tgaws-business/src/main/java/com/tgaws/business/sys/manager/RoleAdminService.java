package com.tgaws.business.sys.manager;

import com.tgaws.business.sys.entity.PermissionEntity;
import com.tgaws.business.sys.entity.RoleEntity;
import com.tgaws.business.sys.mapper.PermissionMapper;
import com.tgaws.business.sys.mapper.RoleMapper;
import com.tgaws.business.sys.mapper.UserCrudMapper;
import com.tgaws.common.exception.BizException;
import com.tgaws.common.result.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色与权限管理服务（T-813：A12~A18）。
 *
 * <p>口径：删除角色前校验无用户占用（A0017）；分配权限=事务内软删旧绑定+重建新绑定；
 * 权限点本身由 04_init_roles.sql 种子维护，运行期只读（A18 出树形结构）。</p>
 */
@Service
public class RoleAdminService {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final UserCrudMapper userCrudMapper;

    public RoleAdminService(RoleMapper roleMapper, PermissionMapper permissionMapper,
                            UserCrudMapper userCrudMapper) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.userCrudMapper = userCrudMapper;
    }

    /** A12 角色列表 */
    public List<RoleEntity> list(String keyword) {
        return roleMapper.selectList(keyword);
    }

    /** A13 新增角色 */
    public Long create(String roleCode, String roleName, String remark, Integer dataScope) {
        if (roleMapper.selectByCode(roleCode) != null) {
            throw new BizException(ErrorCode.A0016);
        }
        RoleEntity entity = new RoleEntity();
        entity.setRoleCode(roleCode);
        entity.setRoleName(roleName);
        entity.setRemark(remark);
        entity.setStatus(1);
        entity.setDataScope(dataScope == null ? 3 : dataScope);
        roleMapper.insert(entity);
        return entity.getId();
    }

    /** A14 修改角色 */
    public void update(long id, String roleName, String remark, Integer dataScope) {
        requireExists(id);
        RoleEntity entity = new RoleEntity();
        entity.setId(id);
        entity.setRoleName(roleName);
        entity.setRemark(remark);
        entity.setDataScope(dataScope);
        roleMapper.update(entity);
    }

    /** A15 删除角色（无用户占用） */
    public void delete(long id) {
        requireExists(id);
        if (userCrudMapper.countUsersByRole(id) > 0) {
            throw new BizException(ErrorCode.A0017);
        }
        roleMapper.logicDelete(id);
    }

    /** A16 角色权限点 id 列表 */
    public List<Long> permissions(long id) {
        requireExists(id);
        return roleMapper.selectPermIdsByRole(id);
    }

    /** A17 分配权限（软删旧+重建） */
    @Transactional
    public void assignPermissions(long id, List<Long> permIds) {
        requireExists(id);
        roleMapper.deleteRolePerms(id);
        if (permIds != null) {
            for (Long permId : permIds) {
                roleMapper.insertRolePerm(id, permId);
            }
        }
    }

    /** A18 权限树（parentId=0 为根，前端树形组件直接消费） */
    public List<PermissionEntity> permissionTree() {
        return PermissionEntity.buildTree(permissionMapper.selectAll());
    }

    private void requireExists(long id) {
        if (roleMapper.selectById(id) == null) {
            throw new BizException(ErrorCode.A0015);
        }
    }
}
