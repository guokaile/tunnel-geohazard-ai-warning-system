-- =====================================================================
-- T-703 审计防篡改 DDL 补丁（对 tgaws 主库与 tgaws_test 测试库各执行一次）
-- 幂等可重跑：审计表补哈希链列；撤销应用账号的 UPDATE/DELETE（8.8 S-08 验收）。
-- =====================================================================

-- 哈希链列（audit_hash = SHA-256(上一行 audit_hash + 本行规范化内容)）
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_oper_log' AND COLUMN_NAME='audit_hash')=0,
  'ALTER TABLE sys_oper_log ADD COLUMN audit_hash char(64) DEFAULT NULL COMMENT ''防篡改哈希链（SHA-256）'' AFTER oper_time',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_login_log' AND COLUMN_NAME='audit_hash')=0,
  'ALTER TABLE sys_login_log ADD COLUMN audit_hash char(64) DEFAULT NULL COMMENT ''防篡改哈希链（SHA-256）'' AFTER login_time',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- =====================================================================
-- 防篡改第二道：撤销应用账号对审计表的 UPDATE/DELETE（部署时执行，替换 <APP_DB_USER>）
--   生产部署（应用账号= tgaws）：
--     REVOKE UPDATE, DELETE ON tgaws.sys_oper_log FROM 'tgaws'@'%';
--     REVOKE UPDATE, DELETE ON tgaws.sys_login_log FROM 'tgaws'@'%';
--   开发/测试库应用连 root 时本撤销无意义（root 不受 REVOKE 约束），
--   防篡改由哈希链 + 仅追加 Mapper（无 update/delete 语句）双保险兜底。
-- =====================================================================
