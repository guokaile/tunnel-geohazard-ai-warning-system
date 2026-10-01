-- =====================================================================
-- TGAWS 建库 + 应用账号（最小权限）—— 仅本地开发环境脚本
-- 开发用法：把下方 __APP_DB_PASSWORD__ 替换为本地开发口令后执行，
--   并同步设置后端环境变量 DB_USERNAME / DB_PASSWORD（见 README 环境变量表）。
-- ⚠ 生产部署不执行本脚本：由 deploy/templates/init-db.cmd 在安装时以
--   向导注入的口令创建账号（口令仅参数传递，脚本零硬编码）。
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `tgaws`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

CREATE USER IF NOT EXISTS 'tgaws_app'@'localhost' IDENTIFIED BY '__APP_DB_PASSWORD__';

GRANT SELECT, INSERT, UPDATE, DELETE, ALTER, CREATE, DROP, INDEX
    ON `tgaws`.* TO 'tgaws_app'@'localhost';

FLUSH PRIVILEGES;
