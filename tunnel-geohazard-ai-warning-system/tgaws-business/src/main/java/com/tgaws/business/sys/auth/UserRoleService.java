package com.tgaws.business.sys.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tgaws.business.sys.mapper.UserMapper;
import com.tgaws.common.datascope.DataScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * 用户授权装载（T-701）：
 *
 * <ul>
 *   <li>权限点/角色/隧道授权三集合一次装载，Caffeine 60s 缓存（权限变更 ≤60s 生效）；</li>
 *   <li>DataScope 组装口径（《4》4.7）：取用户各角色 data_scope 最宽值（MIN 数值），
 *       1=全部（ADMIN/EXPERT/LEADER 种子即全部）；2 本隧道/3 本断面 → sys_role_tunnel
 *       授权隧道集合（无断面授权表，3 按本隧道口径降级，见《6》6.8）；4 仅本人；</li>
 *   <li>无任何角色 → 空权限 + 仅本人（最小可用，越权即 C0008）。</li>
 * </ul>
 */
@Service
public class UserRoleService {

    private static final Logger log = LoggerFactory.getLogger(UserRoleService.class);

    private final UserMapper userMapper;
    private final Cache<Long, Authorities> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(60))
            .build();

    public UserRoleService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /** 授权装载（缓存 60s） */
    public Authorities authorities(long userId) {
        return cache.get(userId, id -> {
            Set<String> permissions = new HashSet<>(userMapper.selectPermissionsByUser(id));
            Set<String> roles = new HashSet<>(userMapper.selectRoleCodesByUser(id));
            Set<Long> tunnelIds = new HashSet<>(userMapper.selectTunnelIdsByUser(id));
            Integer maxScope = userMapper.selectMaxDataScopeByUser(id);
            return new Authorities(permissions, roles, tunnelIds, maxScope);
        });
    }

    /** 数据范围组装（登录态 → DataScope；DataScopeInterceptor 调用） */
    public DataScope buildScope(Authorities authorities, long userId) {
        if (authorities.roles().contains("ADMIN") || authorities.maxScope() == null) {
            // 无角色：不视为全部——最小可用（仅本人）防止越权放大
            if (authorities.roles().contains("ADMIN")) {
                return DataScope.all();
            }
            return DataScope.self(userId);
        }
        return switch (authorities.maxScope()) {
            case DataScope.TYPE_ALL -> DataScope.all();
            // 2 本隧道 / 3 本断面（无断面授权表，按本隧道口径降级，《6》6.8）
            case DataScope.TYPE_TUNNEL, DataScope.TYPE_SECTION ->
                    DataScope.ofTunnels(authorities.tunnelIds());
            default -> DataScope.self(userId);
        };
    }

    /** 权限点校验（拦截器用） */
    public boolean hasPermission(Authorities authorities, String permission) {
        return authorities.permissions().contains(permission);
    }

    /**
     * 授权快照（userId → 权限点/角色/隧道授权/最大数据范围）。
     *
     * @param maxScope null=无任何角色
     */
    public record Authorities(Set<String> permissions, Set<String> roles,
                              Set<Long> tunnelIds, Integer maxScope) {
    }
}
