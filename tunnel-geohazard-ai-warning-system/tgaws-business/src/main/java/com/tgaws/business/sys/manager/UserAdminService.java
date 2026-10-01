package com.tgaws.business.sys.manager;

import com.tgaws.business.sys.entity.UserEntity;
import com.tgaws.business.sys.mapper.UserCrudMapper;
import com.tgaws.business.sys.mapper.UserMapper;
import com.tgaws.common.exception.BizException;
import com.tgaws.common.result.ErrorCode;
import com.tgaws.common.result.PageResult;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户管理服务（T-813：A06~A11）。
 *
 * <p>口径：初始/重置口令统一 {@link #DEFAULT_INIT_PASSWORD}（与《4》A07 初始密码策略一致），
 * pwd_update_time 置 1970-01-01 使密码处于过期态（FR-701 首次登录强制改密）；
 * 删除=逻辑删且禁止删除自己（A0019）；列表不返回 password 哈希。</p>
 */
@Service
public class UserAdminService {

    /** 初始/重置默认口令（安装向导以 ${ADMIN_INIT_PASSWORD} 覆盖管理员初始值） */
    public static final String DEFAULT_INIT_PASSWORD = "Tgaws@123456";

    private final UserCrudMapper userCrudMapper;
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserAdminService(UserCrudMapper userCrudMapper, UserMapper userMapper) {
        this.userCrudMapper = userCrudMapper;
        this.userMapper = userMapper;
    }

    /** A06 用户分页 */
    public PageResult<UserEntity> page(String keyword, Integer status, Long roleId,
                                       int pageNum, int pageSize) {
        long total = userCrudMapper.countPage(keyword, status, roleId);
        if (total == 0) {
            return new PageResult<>(0L, List.of());
        }
        return new PageResult<>(total, userCrudMapper.selectPage(keyword, status, roleId,
                (pageNum - 1) * pageSize, pageSize));
    }

    /** A07 新增用户（初始密码 DEFAULT_INIT_PASSWORD，首次登录强制改密） */
    @Transactional
    public Long create(String username, String realName, String phone, List<Long> roleIds) {
        if (userMapper.selectByUsername(username) != null) {
            throw new BizException(ErrorCode.A0013);
        }
        UserEntity entity = new UserEntity();
        entity.setUsername(username);
        entity.setPassword(passwordEncoder.encode(DEFAULT_INIT_PASSWORD));
        entity.setRealName(realName);
        entity.setPhone(phone);
        entity.setStatus(1);
        userCrudMapper.insert(entity);
        rebindRoles(entity.getId(), roleIds);
        return entity.getId();
    }

    /** A08 修改用户（username 不可改；角色重绑） */
    @Transactional
    public void update(long id, String realName, String phone, List<Long> roleIds) {
        requireExists(id);
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setRealName(realName);
        entity.setPhone(phone);
        userCrudMapper.update(entity);
        rebindRoles(id, roleIds);
    }

    /** A09 删除（逻辑删；禁止删除自己） */
    public void delete(long id, long currentUserId) {
        requireExists(id);
        if (id == currentUserId) {
            throw new BizException(ErrorCode.A0019);
        }
        userCrudMapper.logicDelete(id);
    }

    /** A10 启停 */
    public void updateStatus(long id, int status) {
        requireExists(id);
        userCrudMapper.updateStatus(id, status);
    }

    /** A11 重置密码（DEFAULT_INIT_PASSWORD + 过期态强制改密） */
    public void resetPassword(long id) {
        requireExists(id);
        userCrudMapper.updatePassword(id, passwordEncoder.encode(DEFAULT_INIT_PASSWORD));
    }

    private void requireExists(long id) {
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.A0014);
        }
    }

    private void rebindRoles(long userId, List<Long> roleIds) {
        userCrudMapper.deleteUserRoles(userId);
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                userCrudMapper.insertUserRole(userId, roleId);
            }
        }
    }
}
