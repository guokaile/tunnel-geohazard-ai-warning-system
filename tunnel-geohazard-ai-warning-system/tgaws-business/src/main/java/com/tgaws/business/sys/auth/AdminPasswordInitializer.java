package com.tgaws.business.sys.auth;

import com.tgaws.business.sys.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 初始管理员口令注入（W8：安装向导第 3 步输入 ${ADMIN_INIT_PASSWORD}，
 * 由服务以环境变量注入；应用首启时重哈希落库——与《2》2.12.1 安装流程一致，
 * 避免安装器侧引入 BCrypt 工具链）。
 *
 * <p>幂等口径：仅当 sys_user.admin 的 pwd_update_time 仍为初始化占位值
 * （1970-01-01，02_init_admin.sql 种下的"首次登录强制改密"态）时执行；
 * 运维已改密/非首次启动一律跳过。环境变量未配置同样跳过（源码零硬编码）。</p>
 */
@Component
public class AdminPasswordInitializer {

    private static final Logger log = LoggerFactory.getLogger(AdminPasswordInitializer.class);

    private final UserMapper userMapper;
    private final String adminInitPassword;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminPasswordInitializer(UserMapper userMapper,
                                    @Value("${ADMIN_INIT_PASSWORD:}") String adminInitPassword) {
        this.userMapper = userMapper;
        this.adminInitPassword = adminInitPassword;
    }

    /** 应用就绪后执行一次（幂等；失败仅告警不阻断启动，运维可改密兜底） */
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        if (adminInitPassword == null || adminInitPassword.isEmpty()) {
            return;
        }
        try {
            int updated = userMapper.updateAdminPasswordIfUntouched(
                    passwordEncoder.encode(adminInitPassword));
            if (updated > 0) {
                log.info("初始管理员口令已按 ADMIN_INIT_PASSWORD 注入（admin 强制首次改密态）");
            } else {
                log.info("admin 口令非初始化态，跳过 ADMIN_INIT_PASSWORD 注入");
            }
        } catch (Exception e) {
            log.warn("初始管理员口令注入失败（不阻断启动，请人工登录后修改）：{}", e.getMessage());
        }
    }
}
