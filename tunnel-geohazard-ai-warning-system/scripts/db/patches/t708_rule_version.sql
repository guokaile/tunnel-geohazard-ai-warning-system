-- =====================================================================
-- T-708 规则版本化 DDL 补丁（对 tgaws 主库与 tgaws_test 测试库各执行一次）
-- 幂等可重跑：mon_rule 唯一键 uk_rule_code → uk_rule_version(rule_code, version)
-- （API-C07 修改=版本+1 新行，历史版本留痕，P1-14 规则变更留痕落地）
-- =====================================================================

SET @sql = IF((SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mon_rule' AND INDEX_NAME='uk_rule_version')=0,
  'ALTER TABLE mon_rule DROP INDEX uk_rule_code, ADD UNIQUE KEY uk_rule_version (rule_code, version)',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
