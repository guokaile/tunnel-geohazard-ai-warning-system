-- =====================================================================
-- T-607 报表预聚合 DDL 补丁（对 tgaws 主库与 tgaws_test 测试库各执行一次）
-- 幂等可重跑：rpt_stat_daily 不存在则建表；warn_event.idx_create_time 不存在则补建。
-- 执行方式：mysql tgaws     < t607_rpt_stat_daily.sql
--          mysql tgaws_test < t607_rpt_stat_daily.sql
-- =====================================================================

CREATE TABLE IF NOT EXISTS `rpt_stat_daily` (
    `id`             bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tunnel_id`      bigint unsigned NOT NULL COMMENT '隧道',
    `stat_date`      date NOT NULL COMMENT '统计日',
    `warn_total`     int unsigned NOT NULL DEFAULT 0 COMMENT '当日生成预警数',
    `warn_red`       int unsigned NOT NULL DEFAULT 0 COMMENT '红级预警数',
    `warn_confirmed` int unsigned NOT NULL DEFAULT 0 COMMENT '已确认数',
    `warn_closed`    int unsigned NOT NULL DEFAULT 0 COMMENT '已消警数（warn_status=5）',
    `dispose_total`  int unsigned NOT NULL DEFAULT 0 COMMENT '当日派单数',
    `dispose_closed` int unsigned NOT NULL DEFAULT 0 COMMENT '当日派单中已闭环数（滚动口径）',
    `patrol_total`   int unsigned NOT NULL DEFAULT 0 COMMENT '当日巡检任务数',
    `patrol_done`    int unsigned NOT NULL DEFAULT 0 COMMENT '已完成巡检数',
    `hazard_new`     int unsigned NOT NULL DEFAULT 0 COMMENT '新增隐患数',
    `hazard_closed`  int unsigned NOT NULL DEFAULT 0 COMMENT '闭环隐患数',
    `sample_count`   bigint unsigned NOT NULL DEFAULT 0 COMMENT '实收样本数',
    `sample_expect`  bigint unsigned NOT NULL DEFAULT 0 COMMENT '应收样本数（按点位频率固化，审计 P2-14 落实）',
    `create_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tunnel_date` (`tunnel_id`, `stat_date`),
    KEY `idx_stat_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表日聚合表（FR-801/802 预聚合）';

-- warn_event：补 idx_create_time（驾驶舱"今日预警"实时段查询走索引）
SET @sql = IF((SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='warn_event' AND INDEX_NAME='idx_create_time')=0,
  'ALTER TABLE warn_event ADD KEY idx_create_time (create_time)',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
