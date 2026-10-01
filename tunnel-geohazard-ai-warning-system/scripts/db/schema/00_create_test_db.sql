-- =====================================================================
-- TGAWS 集成测试库（与开发/生产库 tgaws 完全隔离，防测试脏数据污染 T9a 基线）
-- 用法：建库后按序执行 01_tables.sql + data/*.sql（脚本全部幂等，可直接复用）
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `tgaws_test`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

GRANT SELECT, INSERT, UPDATE, DELETE, ALTER, CREATE, DROP, INDEX
    ON `tgaws_test`.* TO 'tgaws_app'@'localhost';

FLUSH PRIVILEGES;
