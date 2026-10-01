-- =====================================================================
-- TGAWS 初始管理员（开发占位）
-- 口令：Tgaws@2026（开发占位口令，BCrypt 哈希内联；pwd_update_time 为
--   1970-01-01 占位态 → 后端强制首次登录改密、改密前不发 token，故占位口令公开安全）
-- 生产：安装向导以 ADMIN_INIT_PASSWORD 环境变量注入后由
--   AdminPasswordInitializer 重新 BCrypt 哈希落库，不执行本脚本。
-- =====================================================================

INSERT IGNORE INTO `sys_user`
    (`id`, `username`, `password`, `real_name`, `phone`, `status`, `pwd_update_time`)
VALUES
    (1, 'admin', '$2b$10$IypV1Qq2d.9VSRC2bL.Tru3Uo1kpNx0DEM2BWgeYZev2E/J/HtQWm',
     '系统管理员', NULL, 1, '1970-01-01 00:00:00');
