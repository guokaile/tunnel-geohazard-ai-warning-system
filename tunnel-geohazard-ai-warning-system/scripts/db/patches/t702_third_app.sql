-- =====================================================================
-- T-702 Open API 安全 DDL 补丁（对 tgaws 主库与 tgaws_test 测试库各执行一次）
-- 幂等可重跑：sys_third_app 补 IP 白名单与密钥轮换过渡列（4.4.3 规格落地）。
-- =====================================================================

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_third_app' AND COLUMN_NAME='ip_whitelist')=0,
  'ALTER TABLE sys_third_app ADD COLUMN ip_whitelist varchar(255) DEFAULT NULL COMMENT ''来源IP白名单（逗号分隔；空=不限）'' AFTER callback_url',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_third_app' AND COLUMN_NAME='secret_old')=0,
  'ALTER TABLE sys_third_app ADD COLUMN secret_old varchar(128) DEFAULT NULL COMMENT ''轮换过渡旧密钥（密文）'' AFTER app_secret',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_third_app' AND COLUMN_NAME='secret_rotate_time')=0,
  'ALTER TABLE sys_third_app ADD COLUMN secret_rotate_time datetime DEFAULT NULL COMMENT ''轮换时间（新旧密钥并行24h过渡窗口）'' AFTER secret_old',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
