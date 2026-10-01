-- =====================================================================
-- TGAWS 数据库初始化脚本：41 张表 DDL（唯一权威源，含 warn_event_timeline）
-- 对应《3.数据库设计说明书》V1.1（3.6 表结构详细设计）
-- 幂等：CREATE TABLE IF NOT EXISTS
-- 规范：InnoDB / utf8mb4 / 全列 COMMENT / uk_ 唯一键 / idx_ 普通索引 / 无外键
-- =====================================================================

-- ---------------------------------------------------------------
-- 系统域（15 张）
-- ---------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`              bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`        varchar(32)  NOT NULL COMMENT '登录名，唯一',
    `password`        varchar(100) NOT NULL COMMENT 'BCrypt 密文',
    `real_name`       varchar(32)  DEFAULT NULL COMMENT '姓名',
    `phone`           varchar(20)  DEFAULT NULL COMMENT '手机号（短信接收）',
    `status`          tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    `last_login_time` datetime     DEFAULT NULL COMMENT '最近登录',
    `last_login_ip`   varchar(45)  DEFAULT NULL COMMENT '最近登录 IP',
    `pwd_update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '密码更新时间（90天策略）',
    `create_by`       bigint unsigned DEFAULT NULL COMMENT '创建人',
    `update_by`       bigint unsigned DEFAULT NULL COMMENT '更新人',
    `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`      tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';

CREATE TABLE IF NOT EXISTS `sys_role` (
    `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_code`   varchar(32) NOT NULL COMMENT '角色编码，唯一',
    `role_name`   varchar(32) NOT NULL COMMENT '角色名称',
    `remark`      varchar(200) DEFAULT NULL COMMENT '备注',
    `status`      tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    `data_scope`  tinyint unsigned NOT NULL DEFAULT 3 COMMENT '数据范围：1全部 2本隧道 3本断面 4仅本人',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`  tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色表';

CREATE TABLE IF NOT EXISTS `sys_user_role` (
    `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     bigint unsigned NOT NULL COMMENT '用户id',
    `role_id`     bigint unsigned NOT NULL COMMENT '角色id',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`  tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户角色关联表';

CREATE TABLE IF NOT EXISTS `sys_role_tunnel` (
    `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_id`     bigint unsigned NOT NULL COMMENT '角色id',
    `tunnel_id`   bigint unsigned NOT NULL COMMENT '隧道id',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`  tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_tunnel` (`role_id`, `tunnel_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色隧道授权表（数据权限多隧道）';

CREATE TABLE IF NOT EXISTS `sys_permission` (
    `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `perm_code`   varchar(64) NOT NULL COMMENT '权限编码，唯一（如 warn:event:close）',
    `perm_name`   varchar(32) NOT NULL COMMENT '权限名称',
    `perm_type`   tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1菜单 2按钮',
    `parent_id`   bigint unsigned NOT NULL DEFAULT 0 COMMENT '父级id，0=根',
    `route`       varchar(128) DEFAULT NULL COMMENT '前端路由',
    `sort`        int NOT NULL DEFAULT 0 COMMENT '排序',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`  tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_perm_code` (`perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='权限点表';

CREATE TABLE IF NOT EXISTS `sys_role_permission` (
    `id`            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_id`       bigint unsigned NOT NULL COMMENT '角色id',
    `permission_id` bigint unsigned NOT NULL COMMENT '权限点id',
    `create_time`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`    tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_perm` (`role_id`, `permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色权限关联表';

CREATE TABLE IF NOT EXISTS `sys_dict_type` (
    `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `dict_code`   varchar(64) NOT NULL COMMENT '字典编码，唯一（如 hazard_type）',
    `dict_name`   varchar(64) NOT NULL COMMENT '字典名称',
    `status`      tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    `remark`      varchar(200) DEFAULT NULL COMMENT '备注',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`  tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dict_code` (`dict_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典类型表';

CREATE TABLE IF NOT EXISTS `sys_dict_item` (
    `id`           bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `dict_type_id` bigint unsigned NOT NULL COMMENT '字典类型id',
    `item_label`   varchar(64) NOT NULL COMMENT '显示名',
    `item_value`   varchar(64) NOT NULL COMMENT '存储值',
    `sort`         int NOT NULL DEFAULT 0 COMMENT '排序',
    `status`       tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    `create_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`   tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_type_value` (`dict_type_id`, `item_value`),
    KEY `idx_type` (`dict_type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典项表';

CREATE TABLE IF NOT EXISTS `sys_config` (
    `id`           bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `config_key`   varchar(64)  NOT NULL COMMENT '配置键，唯一',
    `config_value` varchar(500) NOT NULL COMMENT '配置值（密钥类存占位/密文）',
    `config_type`  tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1普通文本 2密钥',
    `remark`       varchar(200) DEFAULT NULL COMMENT '说明',
    `create_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`   tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统配置表';

CREATE TABLE IF NOT EXISTS `sys_oper_log` (
    `id`             bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`        bigint unsigned DEFAULT NULL COMMENT '操作人id',
    `username`       varchar(32) DEFAULT NULL COMMENT '操作人登录名（快照）',
    `module`         varchar(32) NOT NULL COMMENT '业务模块',
    `operation`      varchar(64) NOT NULL COMMENT '操作内容',
    `method`         varchar(128) DEFAULT NULL COMMENT '类.方法',
    `request_params` varchar(2000) DEFAULT NULL COMMENT '入参（脱敏后）',
    `response_code`  varchar(8) DEFAULT NULL COMMENT '业务响应码',
    `cost_ms`        int DEFAULT NULL COMMENT '耗时毫秒',
    `ip`             varchar(45) DEFAULT NULL COMMENT '来源IP',
    `oper_time`      datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    `audit_hash`     char(64) DEFAULT NULL COMMENT '防篡改哈希链（SHA-256 上一行哈希+本行内容）',
    `create_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_oper_time` (`oper_time`),
    KEY `idx_user_time` (`user_id`, `oper_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='操作审计日志（仅追加，无逻辑删除；哈希链+REVOKE UPDATE/DELETE 防篡改）';

CREATE TABLE IF NOT EXISTS `sys_login_log` (
    `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     bigint unsigned DEFAULT NULL COMMENT '用户id',
    `username`    varchar(32) NOT NULL COMMENT '登录名',
    `login_type`  tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1登录 2登出',
    `result`      tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1成功 0失败',
    `fail_reason` varchar(100) DEFAULT NULL COMMENT '失败原因（密码错误/锁定等）',
    `ip`          varchar(45) DEFAULT NULL COMMENT '来源IP',
    `login_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '时间',
    `audit_hash`  char(64) DEFAULT NULL COMMENT '防篡改哈希链（SHA-256）',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_login_time` (`login_time`),
    KEY `idx_user_time` (`user_id`, `login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='登录日志（仅追加；哈希链防篡改）';

CREATE TABLE IF NOT EXISTS `sys_notify_channel` (
    `id`              bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `channel_code`    varchar(32) NOT NULL COMMENT '通道编码，唯一（light_alarm/sms/inner）',
    `channel_name`    varchar(64) NOT NULL COMMENT '通道名称',
    `channel_type`    tinyint unsigned NOT NULL COMMENT '1声光 2短信 3站内',
    `config_json`     varchar(2000) NOT NULL COMMENT '通道参数JSON（占位符）',
    `enabled`         tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    `health_status`   tinyint unsigned NOT NULL DEFAULT 0 COMMENT '0未知 1正常 2异常',
    `last_check_time` datetime DEFAULT NULL COMMENT '最近健康检测',
    `create_time`     datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`      tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_channel_code` (`channel_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='通知通道配置表';

CREATE TABLE IF NOT EXISTS `sys_third_app` (
    `id`           bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `app_key`      varchar(64)  NOT NULL COMMENT '应用标识，唯一',
    `app_secret`   varchar(128) NOT NULL COMMENT '签名密钥（密文存储）',
    `secret_old`   varchar(128) DEFAULT NULL COMMENT '轮换过渡旧密钥（密文）',
    `secret_rotate_time` datetime DEFAULT NULL COMMENT '轮换时间（新旧密钥并行24h过渡窗口）',
    `app_name`     varchar(64)  NOT NULL COMMENT '应用名称',
    `callback_url` varchar(255) DEFAULT NULL COMMENT '回调地址',
    `ip_whitelist` varchar(255) DEFAULT NULL COMMENT '来源IP白名单（逗号分隔；空=不限）',
    `enabled`      tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    `expire_time`  datetime DEFAULT NULL COMMENT '授权到期',
    `create_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`   tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_app_key` (`app_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='第三方应用注册表';

CREATE TABLE IF NOT EXISTS `sys_backup_log` (
    `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `backup_type` tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1自动 2手动',
    `file_path`   varchar(255) NOT NULL COMMENT '备份文件路径',
    `file_size`   bigint unsigned DEFAULT NULL COMMENT '字节数',
    `result`      tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1成功 0失败',
    `fail_reason` varchar(200) DEFAULT NULL COMMENT '失败原因',
    `backup_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '备份时间',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_backup_time` (`backup_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='备份记录表';

CREATE TABLE IF NOT EXISTS `sys_schema_version` (
    `id`           bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `version`      varchar(32)  NOT NULL COMMENT '版本号（如 1.0.0）',
    `script_name`  varchar(128) NOT NULL COMMENT '脚本文件名',
    `applied_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
    `remark`       varchar(200) DEFAULT NULL COMMENT '说明',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_version` (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='库结构版本表（增量脚本管理）';

-- ---------------------------------------------------------------
-- 工程域（2 张）
-- ---------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `prj_tunnel` (
    `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tunnel_code` varchar(32) NOT NULL COMMENT '隧道编码，唯一',
    `tunnel_name` varchar(64) NOT NULL COMMENT '隧道名称',
    `tunnel_type` tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1公路 2铁路',
    `stage`       tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1施工 2运营',
    `length_m`    decimal(8,2) DEFAULT NULL COMMENT '隧道长度（米）',
    `geo_desc`    varchar(255) DEFAULT NULL COMMENT '地质概况',
    `status`      tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1在建 2正常 3停用',
    `start_date`  date DEFAULT NULL COMMENT '开工日期',
    `remark`      varchar(255) DEFAULT NULL COMMENT '备注',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`  tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tunnel_code` (`tunnel_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='隧道工程表';

CREATE TABLE IF NOT EXISTS `prj_section` (
    `id`            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tunnel_id`     bigint unsigned NOT NULL COMMENT '所属隧道',
    `section_code`  varchar(32) NOT NULL COMMENT '断面编码',
    `section_name`  varchar(64) NOT NULL COMMENT '断面名称',
    `mileage_from`  varchar(32) DEFAULT NULL COMMENT '起始桩号',
    `mileage_to`    varchar(32) DEFAULT NULL COMMENT '结束桩号',
    `geo_zone`      varchar(64) DEFAULT NULL COMMENT '地质分区',
    `stage`         tinyint unsigned NOT NULL DEFAULT 1 COMMENT '工程阶段',
    `status`        tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    `sort`          int NOT NULL DEFAULT 0 COMMENT '排序',
    `create_time`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`    tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tunnel_section` (`tunnel_id`, `section_code`),
    KEY `idx_tunnel` (`tunnel_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='断面分区表';

-- ---------------------------------------------------------------
-- 监测域（3 张）
-- ---------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `mon_gateway` (
    `id`               bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `gateway_code`     varchar(32) NOT NULL COMMENT '网关编号，唯一（协议GW_ID字段）',
    `gateway_name`     varchar(64) NOT NULL COMMENT '网关名称',
    `secret`           varchar(128) NOT NULL COMMENT '注册凭据PSK（密文存储）',
    `protocol`         tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1TCP 2MQTT',
    `scale`            tinyint unsigned NOT NULL DEFAULT 4 COMMENT '协议值缩放系数（实际值=整数×10^-scale）',
    `status`           tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    `online_status`    tinyint unsigned NOT NULL DEFAULT 0 COMMENT '0离线 1在线（心跳维护）',
    `last_online_time` datetime DEFAULT NULL COMMENT '最近在线时间',
    `remark`           varchar(255) DEFAULT NULL COMMENT '备注',
    `create_time`      datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`       tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_gateway_code` (`gateway_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采集网关表';

CREATE TABLE IF NOT EXISTS `mon_point` (
    `id`               bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `point_code`       varchar(32) NOT NULL COMMENT '点位编码，唯一（报文匹配键）',
    `point_name`       varchar(64) NOT NULL COMMENT '点位名称',
    `tunnel_id`        bigint unsigned NOT NULL COMMENT '所属隧道',
    `section_id`       bigint unsigned DEFAULT NULL COMMENT '所属断面',
    `hazard_type`      tinyint unsigned NOT NULL COMMENT '监测对象字典（1坍塌 2涌水突水 3瓦斯及有害气体 4突泥 5沉降 6收敛位移）',
    `item_type`        smallint unsigned NOT NULL COMMENT '测项字典（编码101~602，tinyint上限255会溢出）',
    `unit`             varchar(16) DEFAULT NULL COMMENT '量纲（mm/kN/MPa/L/s/%VOL/ppm）',
    `range_min`        decimal(16,4) DEFAULT NULL COMMENT '量程下限（标定）',
    `range_max`        decimal(16,4) DEFAULT NULL COMMENT '量程上限（标定）',
    `scale`            tinyint unsigned NOT NULL DEFAULT 4 COMMENT '协议值缩放系数：实际值=整数×10^-scale（默认4）',
    `install_position` varchar(128) DEFAULT NULL COMMENT '安装位置描述',
    `gateway_code`     varchar(32) DEFAULT NULL COMMENT '所属采集网关',
    `protocol`         tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1TCP 2MQTT 3人工录入',
    `collect_freq_sec` int NOT NULL DEFAULT 1800 COMMENT '采集周期（秒，30~1800可配）',
    `collect_enabled`  tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1自动采集 0仅人工',
    `enabled`          tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    `online_status`    tinyint unsigned NOT NULL DEFAULT 0 COMMENT '0离线 1在线（应用维护）',
    `last_data_time`   datetime DEFAULT NULL COMMENT '最近数据时间',
    `remark`           varchar(255) DEFAULT NULL COMMENT '备注',
    `create_time`      datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`       tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_point_code` (`point_code`),
    KEY `idx_tunnel_section` (`tunnel_id`, `section_id`),
    KEY `idx_hazard` (`hazard_type`),
    KEY `idx_gateway` (`gateway_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='监测点位表';

CREATE TABLE IF NOT EXISTS `mon_rule` (
    `id`              bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `rule_code`       varchar(32) NOT NULL COMMENT '规则编码，唯一',
    `rule_name`       varchar(64) NOT NULL COMMENT '规则名称',
    `hazard_type`     tinyint unsigned NOT NULL COMMENT '适用监测对象（0=全部）',
    `item_type`       smallint unsigned NOT NULL DEFAULT 0 COMMENT '适用测项（0=全部）',
    `stage`           tinyint unsigned NOT NULL DEFAULT 0 COMMENT '适用工程阶段（0=全阶段）',
    `section_id`      bigint unsigned NOT NULL DEFAULT 0 COMMENT '适用断面（0=全部）',
    `rule_type`       tinyint unsigned NOT NULL COMMENT '1阈值上限 2阈值下限 3速率 4突变 5组合',
    `warn_level`      tinyint unsigned NOT NULL COMMENT '命中定级 1蓝 2黄 3橙 4红',
    `expression_json` varchar(2000) NOT NULL COMMENT '条件表达式/参数JSON（Aviator）',
    `priority`        int NOT NULL DEFAULT 100 COMMENT '优先级（同点命中多规则取最高级别，同级取优先级高者）',
    `version`         int NOT NULL DEFAULT 1 COMMENT '版本号',
    `status`          tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    `effective_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生效时间',
    `expire_time`     datetime DEFAULT NULL COMMENT '失效时间',
    `remark`          varchar(255) DEFAULT NULL COMMENT '备注',
    `create_time`     datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`      tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rule_version` (`rule_code`, `version`),
    KEY `idx_hazard_item_stage` (`hazard_type`, `item_type`, `stage`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='预警规则表（版本化）';

-- ---------------------------------------------------------------
-- 数据域（5 张）
-- ---------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `data_sample` (
    `point_id`     bigint unsigned NOT NULL COMMENT '点位id（主键组成）',
    `ts`           datetime(3)   NOT NULL COMMENT '采样时间毫秒（主键组成+分区键，同点同毫秒天然去重）',
    `value`        decimal(16,4) NOT NULL COMMENT '监测值',
    `quality`      tinyint unsigned NOT NULL DEFAULT 0 COMMENT '质量位：0正常 1超范围 2跳变 3缺失',
    `source`       tinyint unsigned NOT NULL COMMENT '来源：1TCP 2MQTT 3人工 4导入 5第三方',
    `receive_time` datetime(3)   NOT NULL COMMENT '入库时间（毫秒）',
    PRIMARY KEY (`point_id`, `ts`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='原始采样数据表（按月分区+pmax兜底）'
PARTITION BY RANGE COLUMNS(`ts`) (
    PARTITION p202601 VALUES LESS THAN ('2026-02-01'),
    PARTITION p202602 VALUES LESS THAN ('2026-03-01'),
    PARTITION p202603 VALUES LESS THAN ('2026-04-01'),
    PARTITION p202604 VALUES LESS THAN ('2026-05-01'),
    PARTITION p202605 VALUES LESS THAN ('2026-06-01'),
    PARTITION p202606 VALUES LESS THAN ('2026-07-01'),
    PARTITION p202607 VALUES LESS THAN ('2026-08-01'),
    PARTITION p202608 VALUES LESS THAN ('2026-09-01'),
    PARTITION p202609 VALUES LESS THAN ('2026-10-01'),
    PARTITION p202610 VALUES LESS THAN ('2026-11-01'),
    PARTITION p202611 VALUES LESS THAN ('2026-12-01'),
    PARTITION p202612 VALUES LESS THAN ('2027-01-01'),
    PARTITION p202701 VALUES LESS THAN ('2027-02-01'),
    PARTITION p202702 VALUES LESS THAN ('2027-03-01'),
    PARTITION p202703 VALUES LESS THAN ('2027-04-01'),
    PARTITION p202704 VALUES LESS THAN ('2027-05-01'),
    PARTITION p202705 VALUES LESS THAN ('2027-06-01'),
    PARTITION p202706 VALUES LESS THAN ('2027-07-01'),
    PARTITION p202707 VALUES LESS THAN ('2027-08-01'),
    PARTITION p202708 VALUES LESS THAN ('2027-09-01'),
    PARTITION p202709 VALUES LESS THAN ('2027-10-01'),
    PARTITION p202710 VALUES LESS THAN ('2027-11-01'),
    PARTITION p202711 VALUES LESS THAN ('2027-12-01'),
    PARTITION p202712 VALUES LESS THAN ('2028-01-01'),
    PARTITION pmax VALUES LESS THAN (MAXVALUE)
);

CREATE TABLE IF NOT EXISTS `data_sample_minute` (
    `point_id`      bigint unsigned NOT NULL COMMENT '点位id（主键组成）',
    `ts_minute`     datetime(3)   NOT NULL COMMENT '分钟桶时间=桶起始（主键组成+分区键，毫秒精度与采样表一致）',
    `avg_value`     decimal(16,4) NOT NULL COMMENT '均值',
    `min_value`     decimal(16,4) NOT NULL COMMENT '最小值',
    `max_value`     decimal(16,4) NOT NULL COMMENT '最大值',
    `sample_count`  int NOT NULL COMMENT '样本数（>0）',
    `quality_ratio` decimal(5,4) NOT NULL DEFAULT 0 COMMENT '异常样本占比',
    PRIMARY KEY (`point_id`, `ts_minute`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='分钟聚合数据表（按月分区+pmax兜底，仅非空分钟）'
PARTITION BY RANGE COLUMNS(`ts_minute`) (
    PARTITION p202601 VALUES LESS THAN ('2026-02-01'),
    PARTITION p202602 VALUES LESS THAN ('2026-03-01'),
    PARTITION p202603 VALUES LESS THAN ('2026-04-01'),
    PARTITION p202604 VALUES LESS THAN ('2026-05-01'),
    PARTITION p202605 VALUES LESS THAN ('2026-06-01'),
    PARTITION p202606 VALUES LESS THAN ('2026-07-01'),
    PARTITION p202607 VALUES LESS THAN ('2026-08-01'),
    PARTITION p202608 VALUES LESS THAN ('2026-09-01'),
    PARTITION p202609 VALUES LESS THAN ('2026-10-01'),
    PARTITION p202610 VALUES LESS THAN ('2026-11-01'),
    PARTITION p202611 VALUES LESS THAN ('2026-12-01'),
    PARTITION p202612 VALUES LESS THAN ('2027-01-01'),
    PARTITION p202701 VALUES LESS THAN ('2027-02-01'),
    PARTITION p202702 VALUES LESS THAN ('2027-03-01'),
    PARTITION p202703 VALUES LESS THAN ('2027-04-01'),
    PARTITION p202704 VALUES LESS THAN ('2027-05-01'),
    PARTITION p202705 VALUES LESS THAN ('2027-06-01'),
    PARTITION p202706 VALUES LESS THAN ('2027-07-01'),
    PARTITION p202707 VALUES LESS THAN ('2027-08-01'),
    PARTITION p202708 VALUES LESS THAN ('2027-09-01'),
    PARTITION p202709 VALUES LESS THAN ('2027-10-01'),
    PARTITION p202710 VALUES LESS THAN ('2027-11-01'),
    PARTITION p202711 VALUES LESS THAN ('2027-12-01'),
    PARTITION p202712 VALUES LESS THAN ('2028-01-01'),
    PARTITION pmax VALUES LESS THAN (MAXVALUE)
);

CREATE TABLE IF NOT EXISTS `data_point_latest` (
    `point_id`    bigint unsigned NOT NULL COMMENT '点位id（主键，单行/点位）',
    `value`       decimal(16,4) NOT NULL COMMENT '最新值',
    `ts`          datetime(3)   NOT NULL COMMENT '采样时间（毫秒）',
    `quality`     tinyint unsigned NOT NULL DEFAULT 0 COMMENT '质量位',
    `source`      tinyint unsigned NOT NULL COMMENT '来源',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`point_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='点位最新值表（upsert维护）';

CREATE TABLE IF NOT EXISTS `data_import_batch` (
    `id`              bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `batch_no`        varchar(32) NOT NULL COMMENT '批次号，唯一',
    `file_name`       varchar(128) NOT NULL COMMENT '源文件名',
    `import_type`     tinyint unsigned NOT NULL COMMENT '1监测数据 2灾害台账',
    `total_count`     int NOT NULL DEFAULT 0 COMMENT '总行数',
    `success_count`   int NOT NULL DEFAULT 0 COMMENT '成功数',
    `fail_count`      int NOT NULL DEFAULT 0 COMMENT '失败数',
    `status`          tinyint unsigned NOT NULL DEFAULT 0 COMMENT '0待处理 1处理中 2完成 3失败',
    `error_file_path` varchar(255) DEFAULT NULL COMMENT '错误明细文件',
    `finish_time`     datetime DEFAULT NULL COMMENT '完成时间',
    `create_by`       bigint unsigned NOT NULL COMMENT '操作人',
    `create_time`     datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`      tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_batch_no` (`batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文件导入批次表';

CREATE TABLE IF NOT EXISTS `data_archive_log` (
    `id`             bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `table_name`     varchar(64) NOT NULL COMMENT '表名（data_sample/data_sample_minute）',
    `partition_name` varchar(32) NOT NULL COMMENT '分区名（p202601/pmax）',
    `action`         tinyint unsigned NOT NULL COMMENT '1新增分区 2删除分区',
    `boundary_time`  datetime NOT NULL COMMENT '分区上界时间',
    `row_count`      bigint unsigned DEFAULT NULL COMMENT '删除前行数（DROP时记录）',
    `result`         tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1成功 0失败',
    `remark`         varchar(255) DEFAULT NULL COMMENT '说明',
    `create_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
    PRIMARY KEY (`id`),
    KEY `idx_table_action` (`table_name`, `action`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='分区归档留痕表（审计性）';

-- ---------------------------------------------------------------
-- 预警域（5 张）
-- ---------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `warn_event` (
    `id`               bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `event_no`         varchar(32)  NOT NULL COMMENT '事件编号（yyMMdd+6位序号）',
    `tunnel_id`        bigint unsigned NOT NULL COMMENT '隧道',
    `section_id`       bigint unsigned DEFAULT NULL COMMENT '断面',
    `point_id`         bigint unsigned DEFAULT NULL COMMENT '点位（组合规则事件为空）',
    `hazard_type`      tinyint unsigned NOT NULL COMMENT '监测对象',
    `item_type`        smallint unsigned NOT NULL COMMENT '测项',
    `warn_level`       tinyint unsigned NOT NULL COMMENT '级别：1蓝 2黄 3橙 4红',
    `warn_title`       varchar(128) NOT NULL COMMENT '标题',
    `warn_content`     varchar(500) NOT NULL COMMENT '内容（数值/位置/时间）',
    `rule_id`          bigint unsigned DEFAULT NULL COMMENT '命中规则id',
    `trigger_value`    varchar(128) DEFAULT NULL COMMENT '触发值快照',
    `trigger_time`     datetime NOT NULL COMMENT '触发时间',
    `warn_status`      tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1待确认 2已确认 3处置中 4待复核 5已消警 6误报关闭',
    `gate_stage`       tinyint unsigned NOT NULL DEFAULT 0 COMMENT '门禁阶段标记（独立于状态机）：0正常 1影子期（只标记不通知） 2灰度期（橙红人工确认）',
    `hazard_event_id`  bigint unsigned DEFAULT NULL COMMENT '关联灾害登记id（反向关联：一次灾变可关联多条预警事件，FN 计算一对多语义）',
    `confirm_user_id`  bigint unsigned DEFAULT NULL COMMENT '确认人id',
    `confirm_time`     datetime DEFAULT NULL COMMENT '确认时间',
    `confirm_result`   varchar(255) DEFAULT NULL COMMENT '确认结论/误报说明',
    `upgrade_from_id`  bigint unsigned DEFAULT NULL COMMENT '升级来源事件id',
    `close_user_id`    bigint unsigned DEFAULT NULL COMMENT '消警复核人id',
    `close_time`       datetime DEFAULT NULL COMMENT '消警时间',
    `close_reason`     varchar(255) DEFAULT NULL COMMENT '消警原因',
    `snapshot_json`    varchar(4000) DEFAULT NULL COMMENT '数据快照（判定依据留痕）',
    `create_time`      datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_event_no` (`event_no`),
    KEY `idx_tunnel_status` (`tunnel_id`, `warn_status`),
    KEY `idx_level_time` (`warn_level`, `trigger_time`),
    KEY `idx_status` (`warn_status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='预警事件表（永久保留，无逻辑删除）';

CREATE TABLE IF NOT EXISTS `warn_hazard_event` (
    `id`               bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `hazard_no`        varchar(32) NOT NULL COMMENT '登记编号，唯一',
    `tunnel_id`        bigint unsigned NOT NULL COMMENT '隧道',
    `section_id`       bigint unsigned DEFAULT NULL COMMENT '断面',
    `hazard_type`      tinyint unsigned NOT NULL COMMENT '灾害类型（字典 hazard_type）',
    `event_time`       datetime(3) NOT NULL COMMENT '灾害/险情发生时间（模型评估灾变时刻真值）',
    `position`         varchar(128) DEFAULT NULL COMMENT '发生位置',
    `consequence`      varchar(500) DEFAULT NULL COMMENT '后果描述',
    `level`            tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1蓝 2黄 3橙 4红',
    `relate_event_id`  bigint unsigned DEFAULT NULL COMMENT '关联预警事件id',
    `patrol_hazard_id` bigint unsigned DEFAULT NULL COMMENT '巡检隐患来源id（评审3.5 转灾害留痕）',
    `register_user_id` bigint unsigned NOT NULL COMMENT '登记人',
    `remark`           varchar(255) DEFAULT NULL COMMENT '备注',
    `create_time`      datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_hazard_no` (`hazard_no`),
    KEY `idx_tunnel_time` (`tunnel_id`, `event_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='灾害险情登记表（审计性，无逻辑删除）';

CREATE TABLE IF NOT EXISTS `warn_notify_log` (
    `id`              bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `event_id`        bigint unsigned NOT NULL COMMENT '事件id',
    `channel_type`    tinyint unsigned NOT NULL COMMENT '1声光 2短信 3站内',
    `target`          varchar(128) NOT NULL COMMENT '接收对象（人/报警器编号）',
    `content`         varchar(1000) NOT NULL COMMENT '通知内容',
    `status`          tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1成功 2失败 3重试中',
    `retry_count`     int NOT NULL DEFAULT 0 COMMENT '重试次数',
    `send_time`       datetime NOT NULL COMMENT '发送时间',
    `fail_reason`     varchar(255) DEFAULT NULL COMMENT '失败原因',
    `deliver_state`   tinyint unsigned NOT NULL DEFAULT 0 COMMENT '投递状态：0待投递 1已投递 2失败待补偿 3已放弃（Outbox，业务事务内落库）',
    `next_retry_time` datetime(3) DEFAULT NULL COMMENT '下次重试时间（补偿任务扫描）',
    `attempt_count`   int unsigned NOT NULL DEFAULT 0 COMMENT '尝试次数（退避10s/30s/2min/10min，上限6次）',
    `create_time`     datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_event` (`event_id`),
    KEY `idx_send_time` (`send_time`),
    KEY `idx_deliver` (`deliver_state`, `next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='预警通知记录表（Outbox）';

CREATE TABLE IF NOT EXISTS `warn_dispose_task` (
    `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `task_no`     varchar(32) NOT NULL COMMENT '任务编号，唯一',
    `event_id`    bigint unsigned NOT NULL COMMENT '关联事件',
    `assignee_id` bigint unsigned NOT NULL COMMENT '责任人',
    `assigner_id` bigint unsigned NOT NULL COMMENT '派单人',
    `measure`     varchar(500) NOT NULL COMMENT '处置措施',
    `deadline`    datetime NOT NULL COMMENT '完成时限',
    `status`      tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1待处置 2处置中 3已完成 4超时未完成',
    `finish_time` datetime DEFAULT NULL COMMENT '完成时间',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`  tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_no` (`task_no`),
    KEY `idx_event` (`event_id`),
    KEY `idx_assignee_status` (`assignee_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='处置任务表';

CREATE TABLE IF NOT EXISTS `warn_dispose_feedback` (
    `id`               bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `task_id`          bigint unsigned NOT NULL COMMENT '任务id',
    `feedback_user_id` bigint unsigned NOT NULL COMMENT '反馈人',
    `content`          varchar(1000) NOT NULL COMMENT '反馈内容',
    `images`           varchar(2000) DEFAULT NULL COMMENT '图片路径JSON数组',
    `feedback_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '反馈时间',
    `create_time`      datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`       tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='处置反馈表（分次反馈）';

-- ---------------------------------------------------------------
-- 巡检域（6 张）
-- ---------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `patrol_template` (
    `id`            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `template_no`   varchar(32) NOT NULL COMMENT '模板编号，唯一',
    `template_name` varchar(64) NOT NULL COMMENT '模板名称',
    `version`       int NOT NULL DEFAULT 1 COMMENT '版本号',
    `status`        tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    `remark`        varchar(255) DEFAULT NULL COMMENT '备注',
    `create_time`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`    tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_template_version` (`template_no`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='巡检模板表（版本化：修改=同template_no新版本行，历史任务按生成时版本解释）';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='巡检任务表（uk_plan_time 幂等生成：重复生成零副作用）';

CREATE TABLE IF NOT EXISTS `patrol_record` (
    `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `task_id`     bigint unsigned NOT NULL COMMENT '任务id',
    `client_key`  varchar(64) DEFAULT NULL COMMENT '离线补传幂等键（前端暂存UUID，uk）',
    `item_id`     bigint unsigned DEFAULT NULL COMMENT '模板项id',
    `item_name`   varchar(64) NOT NULL COMMENT '巡检项快照',
    `judge_standard_snapshot` varchar(255) DEFAULT NULL COMMENT '判定标准快照（模板改版后历史按旧标准解释）',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='巡检记录表（离线补传幂等：clientKey 唯一+同任务同项唯一）';

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
    `hazard_type`      tinyint unsigned DEFAULT NULL COMMENT '灾害类型（字典hazard_type，转灾害登记用）',
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

-- ---------------------------------------------------------------
-- 分析域（4 张）
-- ---------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `ai_model` (
    `id`             bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `model_code`     varchar(32) NOT NULL COMMENT '模型编码',
    `model_name`     varchar(64) NOT NULL COMMENT '模型名称',
    `model_type`     tinyint unsigned NOT NULL COMMENT '1规则 2统计 3ML（二期预留）',
    `version`        int NOT NULL DEFAULT 1 COMMENT '版本号',
    `params_json`    varchar(2000) NOT NULL COMMENT '运行参数（窗口/系数等）',
    `metrics_json`   varchar(1000) DEFAULT NULL COMMENT '评估指标（准确率/漏报率/误报率）',
    `is_current`     tinyint unsigned NOT NULL DEFAULT 0 COMMENT '1当前生效 0历史版本',
    `status`         tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    `effective_time` datetime DEFAULT NULL COMMENT '生效时间',
    `create_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`     tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_model_version` (`model_code`, `version`),
    KEY `idx_model_code` (`model_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='算法模型注册表';

CREATE TABLE IF NOT EXISTS `ai_forecast` (
    `id`             bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `point_id`       bigint unsigned NOT NULL COMMENT '点位id',
    `model_id`       bigint unsigned NOT NULL COMMENT '模型id',
    `forecast_at`    datetime NOT NULL COMMENT '批算时刻',
    `target_time`    datetime NOT NULL COMMENT '预测目标时刻',
    `forecast_value` decimal(16,4) NOT NULL COMMENT '预测值',
    `lower_bound`    decimal(16,4) DEFAULT NULL COMMENT '置信区间下界',
    `upper_bound`    decimal(16,4) DEFAULT NULL COMMENT '置信区间上界',
    `create_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_point_target` (`point_id`, `target_time`, `forecast_at`),
    KEY `idx_forecast_at` (`forecast_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='预测结果表（保留90天，回测/查询）';

CREATE TABLE IF NOT EXISTS `rpt_stat_daily` (
    `id`             bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tunnel_id`      bigint unsigned NOT NULL COMMENT '隧道',
    `stat_date`      date NOT NULL COMMENT '统计日',
    `warn_total`     int unsigned NOT NULL DEFAULT 0 COMMENT '当日生成预警数',
    `warn_red`       int unsigned NOT NULL DEFAULT 0 COMMENT '红级预警数',
    `warn_confirmed` int unsigned NOT NULL DEFAULT 0 COMMENT '已确认数（warn_status>=2 且 !=6 误报口径）',
    `warn_closed`    int unsigned NOT NULL DEFAULT 0 COMMENT '已消警数（warn_status=5）',
    `dispose_total`  int unsigned NOT NULL DEFAULT 0 COMMENT '当日派单数',
    `dispose_closed` int unsigned NOT NULL DEFAULT 0 COMMENT '当日派单中已闭环数（滚动口径，闭环率=closed/total）',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表日聚合表（FR-801/802 预聚合：FR-802 驾驶舱 ≤1min 刷新的物理前提，实时聚合扫千万行不可能达标）';

CREATE TABLE IF NOT EXISTS `rpt_export_task` (
    `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `task_no`     varchar(32) NOT NULL COMMENT '任务编号，唯一',
    `export_type` tinyint unsigned NOT NULL COMMENT '1监测数据 2预警记录 3巡检记录',
    `params_json` varchar(2000) NOT NULL COMMENT '查询条件JSON',
    `file_path`   varchar(255) DEFAULT NULL COMMENT '导出文件路径',
    `status`      tinyint unsigned NOT NULL DEFAULT 0 COMMENT '0排队 1处理中 2完成 3失败',
    `fail_reason` varchar(255) DEFAULT NULL COMMENT '失败原因',
    `finish_time` datetime DEFAULT NULL COMMENT '完成时间',
    `create_by`   bigint unsigned NOT NULL COMMENT '操作人',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`  tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_no` (`task_no`),
    KEY `idx_create_by` (`create_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='数据导出任务表';

CREATE TABLE IF NOT EXISTS `rpt_report` (
    `id`            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `report_no`     varchar(32) NOT NULL COMMENT '报告编号，唯一',
    `report_type`   tinyint unsigned NOT NULL COMMENT '1日报 2周报 3月报',
    `period`        varchar(16) NOT NULL COMMENT '统计周期（YYYYMMDD/YYYYWW/YYYYMM）',
    `file_path`     varchar(255) NOT NULL COMMENT '报告文件路径',
    `status`        tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1生成成功 0生成失败',
    `generate_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    `create_time`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`    tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_report_no` (`report_no`),
    KEY `idx_type_period` (`report_type`, `period`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='分析报告记录表';

CREATE TABLE IF NOT EXISTS `warn_event_timeline` (
    `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `event_id`    bigint unsigned NOT NULL COMMENT '预警事件id',
    `node_type`   tinyint unsigned NOT NULL COMMENT '节点类型：1生成 2通知 3确认 4误报 5派单 6反馈 7升级 8降级 9待复核 10消警 11灾变确认',
    `actor_type`  tinyint unsigned NOT NULL DEFAULT 1 COMMENT '1系统 2人工',
    `actor_id`    bigint unsigned DEFAULT NULL COMMENT '操作人id（系统为NULL）',
    `actor_name`  varchar(32) DEFAULT NULL COMMENT '操作人快照（防用户改名后失真）',
    `action`      varchar(64) NOT NULL COMMENT '动作摘要',
    `detail`      varchar(1000) DEFAULT NULL COMMENT '详情（含触发值快照，不可被后续覆盖）',
    `occur_time`  datetime(3) NOT NULL COMMENT '发生时间',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    PRIMARY KEY (`id`),
    KEY `idx_event_time` (`event_id`, `occur_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='预警事件时间线（仅追加，不可篡改）';
