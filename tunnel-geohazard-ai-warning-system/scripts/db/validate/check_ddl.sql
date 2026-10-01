-- =====================================================================
-- TGAWS DDL 静态校验（T-205/W2 出口标准）
-- 用法：mysql tgaws < validate/check_ddl.sql
-- 判定标准：以下每条查询结果为空 = 通过；任一行输出即构建/验收失败。
-- 覆盖：表数量、关键列类型（smallint/datetime(3)）、主键去重语义、
--       pmax 兜底分区、字符集。
-- =====================================================================

-- 1) 表数量必须等于 42（含 warn_event_timeline、T-607 rpt_stat_daily）
SELECT CONCAT('FAIL: table_count=', cnt) FROM
    (SELECT COUNT(*) AS cnt FROM information_schema.TABLES
      WHERE TABLE_SCHEMA='tgaws' AND TABLE_TYPE='BASE TABLE') t
WHERE cnt <> 42;

-- 2) item_type 必须为 smallint unsigned（tinyint 会溢出，编码 101~602）
SELECT CONCAT('FAIL: item_type=', COLUMN_TYPE) FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='tgaws'
  AND TABLE_NAME IN ('mon_point','mon_rule','warn_event')
  AND COLUMN_NAME='item_type'
  AND COLUMN_TYPE <> 'smallint unsigned';

-- 3) 毫秒精度字段必须为 datetime(3)
SELECT CONCAT('FAIL: ', TABLE_NAME, '.', COLUMN_NAME, '=', COLUMN_TYPE) FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='tgaws'
  AND ((TABLE_NAME='data_sample' AND COLUMN_NAME IN ('ts','receive_time'))
    OR (TABLE_NAME='data_sample_minute' AND COLUMN_NAME='ts_minute')
    OR (TABLE_NAME='data_point_latest' AND COLUMN_NAME='ts')
    OR (TABLE_NAME='warn_hazard_event' AND COLUMN_NAME='event_time'))
  AND COLUMN_TYPE <> 'datetime(3)';

-- 4) 主键去重语义：data_sample / data_sample_minute 主键必须为 (point_id, ts/ts_minute)
SELECT CONCAT('FAIL: pk_of_', TABLE_NAME) FROM
    (SELECT 'data_sample' AS TABLE_NAME, 'point_id,ts' AS expect UNION ALL
     SELECT 'data_sample_minute', 'point_id,ts_minute') e
JOIN (SELECT TABLE_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS pk
      FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA='tgaws' AND INDEX_NAME='PRIMARY'
      GROUP BY TABLE_NAME) p USING (TABLE_NAME)
WHERE p.pk <> e.expect;

-- 5) 分区表必须存在 pmax MAXVALUE 兜底
SELECT CONCAT('FAIL: pmax_missing_in_', TABLE_NAME) FROM (
    SELECT 'data_sample' AS TABLE_NAME UNION ALL SELECT 'data_sample_minute') e
LEFT JOIN (SELECT TABLE_NAME FROM information_schema.PARTITIONS
           WHERE TABLE_SCHEMA='tgaws' AND PARTITION_NAME='pmax'
           GROUP BY TABLE_NAME) p USING (TABLE_NAME)
WHERE p.TABLE_NAME IS NULL;

-- 6) 全部表排序规则必须为 utf8mb4
SELECT CONCAT('FAIL: collation_of_', TABLE_NAME, '=', TABLE_COLLATION)
FROM information_schema.TABLES
WHERE TABLE_SCHEMA='tgaws' AND TABLE_COLLATION NOT LIKE 'utf8mb4%';

-- 7) 逻辑删除字段规约：业务表 is_deleted 必须 tinyint unsigned
SELECT CONCAT('FAIL: is_deleted_of_', TABLE_NAME, '=', COLUMN_TYPE)
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='tgaws' AND COLUMN_NAME='is_deleted'
  AND COLUMN_TYPE <> 'tinyint unsigned';

-- 8) 通知 Outbox 投递状态列存在（deliver_state/next_retry_time/attempt_count）
SELECT 'FAIL: warn_notify_log.outbox_columns_missing' WHERE
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA='tgaws' AND TABLE_NAME='warn_notify_log'
        AND COLUMN_NAME IN ('deliver_state','next_retry_time','attempt_count')) <> 3;

-- 9) 分区完整性：两分区表必须为 25 个分区（当年+次年 24 个月 + pmax 兜底）
SELECT CONCAT('FAIL: partition_count_of_', TABLE_NAME, '=', cnt) FROM (
    SELECT TABLE_NAME, COUNT(*) AS cnt FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA='tgaws' AND TABLE_NAME IN ('data_sample','data_sample_minute')
    GROUP BY TABLE_NAME) p
WHERE cnt <> 25;

-- 10) 影子门禁独立字段存在（warn_event.gate_stage，不复用 warn_status 状态机）
SELECT 'FAIL: warn_event.gate_stage missing' WHERE
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA='tgaws' AND TABLE_NAME='warn_event'
        AND COLUMN_NAME='gate_stage') <> 1;

-- 11) 时间线仅追加表存在（FR-406 不可篡改落地点）+ 灾变关联字段
SELECT 'FAIL: warn_event_timeline missing' WHERE
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA='tgaws' AND TABLE_NAME='warn_event_timeline') <> 1;
SELECT 'FAIL: warn_event.hazard_event_id missing' WHERE
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA='tgaws' AND TABLE_NAME='warn_event'
        AND COLUMN_NAME='hazard_event_id') <> 1;

-- 12) T-606 巡检域字段与幂等约束（FR-501/502/504 + 评审 3.3/3.4/3.5）
SELECT 'FAIL: patrol_task.template_id missing' WHERE
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA='tgaws' AND TABLE_NAME='patrol_task'
        AND COLUMN_NAME='template_id') <> 1;
SELECT 'FAIL: patrol_plan.inspector_id missing' WHERE
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA='tgaws' AND TABLE_NAME='patrol_plan'
        AND COLUMN_NAME='inspector_id') <> 1;
SELECT 'FAIL: patrol_record.client_key missing' WHERE
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA='tgaws' AND TABLE_NAME='patrol_record'
        AND COLUMN_NAME='client_key') <> 1;
SELECT 'FAIL: warn_hazard_event.patrol_hazard_id missing' WHERE
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA='tgaws' AND TABLE_NAME='warn_hazard_event'
        AND COLUMN_NAME='patrol_hazard_id') <> 1;
-- 幂等唯一键：任务生成（uk_plan_time）/ 补传（uk_client_key/uk_task_item）/ 模板版本（uk_template_version）
-- 注：STATISTICS 每索引一行/列，复合索引须 COUNT(DISTINCT INDEX_NAME)
SELECT 'FAIL: patrol_task.uk_plan_time missing' WHERE
    (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA='tgaws' AND TABLE_NAME='patrol_task'
        AND INDEX_NAME='uk_plan_time') <> 1;
SELECT 'FAIL: patrol_record.uk_client_key missing' WHERE
    (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA='tgaws' AND TABLE_NAME='patrol_record'
        AND INDEX_NAME='uk_client_key') <> 1;
SELECT 'FAIL: patrol_record.uk_task_item missing' WHERE
    (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA='tgaws' AND TABLE_NAME='patrol_record'
        AND INDEX_NAME='uk_task_item') <> 1;
SELECT 'FAIL: patrol_template.uk_template_version missing' WHERE
    (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA='tgaws' AND TABLE_NAME='patrol_template'
        AND INDEX_NAME='uk_template_version') <> 1;

-- 13) T-607 报表预聚合表存在（FR-801/802 预聚合口径）+ 预警事件按日统计索引
SELECT 'FAIL: rpt_stat_daily missing' WHERE
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA='tgaws' AND TABLE_NAME='rpt_stat_daily') <> 1;
SELECT 'FAIL: warn_event.idx_create_time missing' WHERE
    (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA='tgaws' AND TABLE_NAME='warn_event'
        AND INDEX_NAME='idx_create_time') <> 1;

-- 14) T-702 Open API 安全字段（IP 白名单/密钥轮换过渡）
SELECT 'FAIL: sys_third_app.ip_whitelist missing' WHERE
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA='tgaws' AND TABLE_NAME='sys_third_app'
        AND COLUMN_NAME='ip_whitelist') <> 1;
SELECT 'FAIL: sys_third_app.secret_old missing' WHERE
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA='tgaws' AND TABLE_NAME='sys_third_app'
        AND COLUMN_NAME='secret_old') <> 1;

-- 15) T-703 审计防篡改哈希链列
SELECT 'FAIL: sys_oper_log.audit_hash missing' WHERE
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA='tgaws' AND TABLE_NAME='sys_oper_log'
        AND COLUMN_NAME='audit_hash') <> 1;
SELECT 'FAIL: sys_login_log.audit_hash missing' WHERE
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA='tgaws' AND TABLE_NAME='sys_login_log'
        AND COLUMN_NAME='audit_hash') <> 1;

-- 16) T-708 规则版本化唯一键（API-C07 修改=版本+1 新行）
SELECT 'FAIL: mon_rule.uk_rule_version missing' WHERE
    (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA='tgaws' AND TABLE_NAME='mon_rule'
        AND INDEX_NAME='uk_rule_version') <> 1;
