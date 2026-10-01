-- =====================================================================
-- TGAWS 分区维护参考脚本（对应《3.数据库设计说明书》3.5.1）
-- 说明：初始分区（当年+次年共 24 个 + pmax 兜底）已内联在 01_tables.sql
-- 的 CREATE TABLE 中；本脚本为定时任务（每月 1 日 00:10）的执行模板。
--
-- 纪律（《6.详细设计说明书》6.10 PartitionDdlMapper）：
--   1) ADD PARTITION 永远保留 pmax 兜底（建分区失败数据不丢）；
--   2) DROP PARTITION 前三重断言：分区存在 / 上界已过期 / 行数已归档；
--   3) 每次 ADD/DROP 必须写 data_archive_log 留痕。
-- =====================================================================

-- 一、每月新增下月分区（示例：为两表新增 2028-01 分区）
--   由应用定时任务生成，参数为计算后的月份上界；
--   注意：pmax 兜底分区存在时，MySQL 禁止在其后 ADD PARTITION（ERROR 1503），
--   必须用 REORGANIZE 将 pmax 拆分为（新月份 + pmax）。
ALTER TABLE `data_sample` REORGANIZE PARTITION pmax INTO (
    PARTITION p202801 VALUES LESS THAN ('2028-02-01'),
    PARTITION pmax VALUES LESS THAN (MAXVALUE)
);
ALTER TABLE `data_sample_minute` REORGANIZE PARTITION pmax INTO (
    PARTITION p202801 VALUES LESS THAN ('2028-02-01'),
    PARTITION pmax VALUES LESS THAN (MAXVALUE)
);

-- 二、到期分区归档删除（示例：原始表 2026-01 已超 1 年保留期）
--   前置断言（应用侧先查再删）：
--     SELECT PARTITION_NAME FROM information_schema.PARTITIONS
--      WHERE TABLE_SCHEMA='tgaws' AND TABLE_NAME='data_sample'
--        AND PARTITION_NAME='p202601';
--     SELECT COUNT(*) FROM data_sample WHERE ts >= '2026-01-01' AND ts < '2026-02-01';
--   行数已归档（配置开关开启时先导出 CSV）后执行：
ALTER TABLE `data_sample` DROP PARTITION p202601;
ALTER TABLE `data_sample_minute` DROP PARTITION p202601;

-- 三、留痕（应用侧随 DDL 同事务外顺序写入）
INSERT INTO `data_archive_log`
    (table_name, partition_name, action, boundary_time, row_count, result, remark)
VALUES
    ('data_sample', 'p202601', 2, '2026-02-01', 4800000, 1, '超期归档删除（示例）');
