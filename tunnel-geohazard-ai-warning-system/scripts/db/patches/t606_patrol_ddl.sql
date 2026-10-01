-- =====================================================================
-- T-606 巡检域 DDL 补丁（对 tgaws 主库与 tgaws_test 测试库各执行一次）
-- 幂等可重跑：表不存在则按终版建表；已存在则按列/键逐一补全。
-- 执行方式：mysql tgaws     < t606_patrol_ddl.sql
--          mysql tgaws_test < t606_patrol_ddl.sql
-- 核验：   mysql tgaws < ../validate/check_ddl.sql   （输出为空=全部通过）
-- =====================================================================

-- ---------- 6 张巡检表（终版 DDL，与 01_tables.sql 一致） ----------
CREATE TABLE IF NOT EXISTS `patrol_template` (
    `id`            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `template_no`   varchar(32) NOT NULL COMMENT '模板编号（跨版本同号）',
    `template_name` varchar(64) NOT NULL COMMENT '模板名称',
    `version`       int NOT NULL DEFAULT 1 COMMENT '版本号',
    `status`        tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    `remark`        varchar(255) DEFAULT NULL COMMENT '备注',
    `create_time`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`    tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_template_version` (`template_no`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='巡检模板表（版本化：修改=同template_no新版本行）';

CREATE TABLE IF NOT EXISTS `patrol_template_item` (
    `id`             bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `template_id`    bigint unsigned NOT NULL COMMENT '模板id',
    `item_name`      varchar(64) NOT NULL COMMENT '巡检项',
    `check_content`  varchar(255) NOT NULL COMMENT '检查内容',
    `judge_standard` varchar(255) DEFAULT NULL COMMENT '判定标准',
    `sort`           int NOT NULL DEFAULT 0 COMMENT '排序',
    `create_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`     tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_template` (`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='巡检模板项表';

CREATE TABLE IF NOT EXISTS `patrol_plan` (
    `id`             bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `plan_no`        varchar(32) NOT NULL COMMENT '计划编号，唯一',
    `plan_name`      varchar(64) NOT NULL COMMENT '计划名称',
    `tunnel_id`      bigint unsigned NOT NULL COMMENT '隧道',
    `frequency_type` tinyint unsigned NOT NULL COMMENT '1每班 2每日 3每周',
    `time_slot`      varchar(32) NOT NULL COMMENT '班次/时间点（如08:00、夜班）',
    `template_id`    bigint unsigned NOT NULL COMMENT '关联模板（版本行id）',
    `inspector_id`   bigint unsigned NOT NULL COMMENT '巡检人（自动派发对象）',
    `enabled`        tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    `create_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`     tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plan_no` (`plan_no`),
    KEY `idx_tunnel` (`tunnel_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='巡检计划表';

CREATE TABLE IF NOT EXISTS `patrol_task` (
    `id`           bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `task_no`      varchar(32) NOT NULL COMMENT '任务编号，唯一',
    `plan_id`      bigint unsigned NOT NULL COMMENT '来源计划',
    `tunnel_id`    bigint unsigned NOT NULL COMMENT '隧道',
    `section_id`   bigint unsigned DEFAULT NULL COMMENT '断面',
    `inspector_id` bigint unsigned NOT NULL COMMENT '巡检人',
    `template_id`  bigint unsigned NOT NULL COMMENT '模板版本行id（完成校验与历史快照口径）',
    `plan_time`    datetime NOT NULL COMMENT '计划巡检时间',
    `status`       tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1待巡检 2进行中 3已完成 4逾期',
    `finish_time`  datetime DEFAULT NULL COMMENT '完成时间',
    `create_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`   tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_no` (`task_no`),
    UNIQUE KEY `uk_plan_time` (`plan_id`, `plan_time`),
    KEY `idx_inspector_status` (`inspector_id`, `status`),
    KEY `idx_plan_time` (`plan_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='巡检任务表（uk_plan_time 幂等生成）';

CREATE TABLE IF NOT EXISTS `patrol_record` (
    `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `task_id`     bigint unsigned NOT NULL COMMENT '任务id',
    `client_key`  varchar(64) DEFAULT NULL COMMENT '离线补传幂等键（前端暂存UUID，uk）',
    `item_id`     bigint unsigned DEFAULT NULL COMMENT '模板项id',
    `item_name`   varchar(64) NOT NULL COMMENT '巡检项快照',
    `judge_standard_snapshot` varchar(255) DEFAULT NULL COMMENT '判定标准快照',
    `result`      tinyint unsigned NOT NULL COMMENT '1正常 2异常 3不适用',
    `description` varchar(500) DEFAULT NULL COMMENT '情况描述',
    `images`      varchar(2000) DEFAULT NULL COMMENT '图片路径JSON数组',
    `longitude`   decimal(10,6) DEFAULT NULL COMMENT '经度',
    `latitude`    decimal(10,6) DEFAULT NULL COMMENT '纬度',
    `record_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '填报时间',
    `recorder_id` bigint unsigned NOT NULL COMMENT '填报人',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`  tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_client_key` (`client_key`),
    UNIQUE KEY `uk_task_item` (`task_id`, `item_id`),
    KEY `idx_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='巡检记录表（离线补传幂等）';

CREATE TABLE IF NOT EXISTS `patrol_hazard` (
    `id`               bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `hazard_no`        varchar(32) NOT NULL COMMENT '隐患编号，唯一',
    `tunnel_id`        bigint unsigned NOT NULL COMMENT '隧道',
    `section_id`       bigint unsigned DEFAULT NULL COMMENT '断面',
    `source`           tinyint unsigned NOT NULL COMMENT '1巡检发现 2人工上报',
    `title`            varchar(128) NOT NULL COMMENT '隐患标题',
    `description`      varchar(1000) DEFAULT NULL COMMENT '隐患描述',
    `hazard_level`     tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1蓝 2黄 3橙 4红',
    `images`           varchar(2000) DEFAULT NULL COMMENT '图片路径JSON数组',
    `longitude`        decimal(10,6) DEFAULT NULL COMMENT '经度',
    `latitude`         decimal(10,6) DEFAULT NULL COMMENT '纬度',
    `status`           tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1待处置 2处置中 3已闭环',
    `handler_id`       bigint unsigned DEFAULT NULL COMMENT '处置人',
    `task_id`          bigint unsigned DEFAULT NULL COMMENT '来源巡检任务id',
    `record_id`        bigint unsigned DEFAULT NULL COMMENT '来源巡检记录id',
    `hazard_type`      tinyint unsigned DEFAULT NULL COMMENT '灾害类型（字典hazard_type）',
    `discover_user_id` bigint unsigned NOT NULL COMMENT '发现人',
    `discover_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发现时间',
    `close_time`       datetime DEFAULT NULL COMMENT '闭环时间',
    `close_remark`     varchar(255) DEFAULT NULL COMMENT '闭环说明',
    `create_time`      datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`       tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_hazard_no` (`hazard_no`),
    KEY `idx_tunnel_status` (`tunnel_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='隐患登记表';

-- ---------- warn_hazard_event 补列（评审 3.5 巡检隐患转灾害留痕） ----------
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='warn_hazard_event' AND COLUMN_NAME='patrol_hazard_id')=0,
  'ALTER TABLE warn_hazard_event ADD COLUMN patrol_hazard_id bigint unsigned DEFAULT NULL COMMENT ''巡检隐患来源id'' AFTER relate_event_id',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 旧表缺列/缺键补全（表已存在但为旧版草稿时） ----------

-- patrol_template：uk_template_no → uk_template_version
SET @sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='patrol_template' AND INDEX_NAME='uk_template_version')=0,
  'ALTER TABLE patrol_template DROP INDEX uk_template_no, ADD UNIQUE KEY uk_template_version (template_no, version)',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- patrol_plan：+ inspector_id
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='patrol_plan' AND COLUMN_NAME='inspector_id')=0,
  'ALTER TABLE patrol_plan ADD COLUMN inspector_id bigint unsigned NOT NULL DEFAULT 0 COMMENT ''巡检人'' AFTER template_id',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- patrol_task：+ template_id / + uk_plan_time
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='patrol_task' AND COLUMN_NAME='template_id')=0,
  'ALTER TABLE patrol_task ADD COLUMN template_id bigint unsigned NOT NULL DEFAULT 0 COMMENT ''模板版本行id'' AFTER inspector_id',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='patrol_task' AND INDEX_NAME='uk_plan_time')=0,
  'ALTER TABLE patrol_task ADD UNIQUE KEY uk_plan_time (plan_id, plan_time)',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- patrol_record：+ client_key / judge_standard_snapshot / uk_client_key / uk_task_item
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='patrol_record' AND COLUMN_NAME='client_key')=0,
  'ALTER TABLE patrol_record ADD COLUMN client_key varchar(64) DEFAULT NULL COMMENT ''离线补传幂等键'' AFTER task_id',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='patrol_record' AND COLUMN_NAME='judge_standard_snapshot')=0,
  'ALTER TABLE patrol_record ADD COLUMN judge_standard_snapshot varchar(255) DEFAULT NULL COMMENT ''判定标准快照'' AFTER item_name',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='patrol_record' AND INDEX_NAME='uk_client_key')=0,
  'ALTER TABLE patrol_record ADD UNIQUE KEY uk_client_key (client_key)',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='patrol_record' AND INDEX_NAME='uk_task_item')=0,
  'ALTER TABLE patrol_record ADD UNIQUE KEY uk_task_item (task_id, item_id)',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- patrol_hazard：+ 经纬度/来源追溯/灾害类型
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='patrol_hazard' AND COLUMN_NAME='longitude')=0,
  'ALTER TABLE patrol_hazard ADD COLUMN longitude decimal(10,6) DEFAULT NULL COMMENT ''经度'' AFTER images',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='patrol_hazard' AND COLUMN_NAME='latitude')=0,
  'ALTER TABLE patrol_hazard ADD COLUMN latitude decimal(10,6) DEFAULT NULL COMMENT ''纬度'' AFTER longitude',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='patrol_hazard' AND COLUMN_NAME='task_id')=0,
  'ALTER TABLE patrol_hazard ADD COLUMN task_id bigint unsigned DEFAULT NULL COMMENT ''来源巡检任务id'' AFTER handler_id',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='patrol_hazard' AND COLUMN_NAME='record_id')=0,
  'ALTER TABLE patrol_hazard ADD COLUMN record_id bigint unsigned DEFAULT NULL COMMENT ''来源巡检记录id'' AFTER task_id',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='patrol_hazard' AND COLUMN_NAME='hazard_type')=0,
  'ALTER TABLE patrol_hazard ADD COLUMN hazard_type tinyint unsigned DEFAULT NULL COMMENT ''灾害类型（字典hazard_type）'' AFTER record_id',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
