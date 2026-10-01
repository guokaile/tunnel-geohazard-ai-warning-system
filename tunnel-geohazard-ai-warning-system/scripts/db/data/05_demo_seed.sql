-- =====================================================================
-- W8 演示种子（开发/演示库；安装包 --demo 模式在 t2_seed.sql 之后执行本文件）：
--   依赖 t2_seed.sql（隧道 T2-T1 + 1000 点位 + 网关 GW001）已执行；
--   1) T2-T1 补 3 个断面，前 30 个点位分配到断面（断面图 B19 数据源）；
--   2) 前 30 点位生成近 2 小时样本（30s 周期，瓦斯 %VOL 正弦漂移）+ 最新值；
--   3) 6 条预警事件覆盖状态机全状态（1待确认/2已确认/3处置中/4待复核/5已消警/6误报）+ 时间线；
--   4) 模板2+计划2、2 条当日任务（1已完成+1逾期，生成任务幂等预检"跳过 2 条"）、
--      已完成任务 5 条填报记录、1 条已闭环隐患（来源巡检记录）；
--   5) 2 条处置任务（驾驶舱闭环率口径）+ 近 7 日报表聚合（日/周/月报与趋势图数据源）；
--   供 F2 实时监控/F3 预警中心/巡检管理/报表中心联调与演示。
-- 幂等：重复执行不产生重复行（点位更新覆盖；样本 upsert；事件按 event_no 防重；
--       任务按 uk_plan_time、记录按 uk_task_item、隐患/处置按编号、报表按 uk_tunnel_date）。
-- =====================================================================

SET SESSION cte_max_recursion_depth = 2000;

-- ---------- 1. 断面（T2-T1 补 3 个） ----------
INSERT INTO prj_section (tunnel_id, section_code, section_name, mileage_from, mileage_to, geo_zone, status, sort)
SELECT id, 'SEC-01', '进口段', 'K0+000', 'K0+600', 'V级围岩·浅埋段', 1, 0
FROM prj_tunnel WHERE tunnel_code = 'T2-T1' AND NOT EXISTS (
    SELECT 1 FROM prj_section s WHERE s.tunnel_id = prj_tunnel.id AND s.section_code = 'SEC-01');
INSERT INTO prj_section (tunnel_id, section_code, section_name, mileage_from, mileage_to, geo_zone, status, sort)
SELECT id, 'SEC-02', '洞身段', 'K0+600', 'K1+400', 'IV级围岩·断层破碎带', 1, 1
FROM prj_tunnel WHERE tunnel_code = 'T2-T1' AND NOT EXISTS (
    SELECT 1 FROM prj_section s WHERE s.tunnel_id = prj_tunnel.id AND s.section_code = 'SEC-02');
INSERT INTO prj_section (tunnel_id, section_code, section_name, mileage_from, mileage_to, geo_zone, status, sort)
SELECT id, 'SEC-03', '出口段', 'K1+400', 'K2+000', 'V级围岩·偏压段', 1, 2
FROM prj_tunnel WHERE tunnel_code = 'T2-T1' AND NOT EXISTS (
    SELECT 1 FROM prj_section s WHERE s.tunnel_id = prj_tunnel.id AND s.section_code = 'SEC-03');

-- ---------- 2. 前 30 点位分配断面（覆盖：覆盖执行） ----------
UPDATE mon_point p
JOIN prj_section s ON s.tunnel_id = p.tunnel_id
SET p.section_id = s.id
WHERE p.point_code BETWEEN 'P00010001' AND 'P00010010' AND s.section_code = 'SEC-01';
UPDATE mon_point p
JOIN prj_section s ON s.tunnel_id = p.tunnel_id
SET p.section_id = s.id
WHERE p.point_code BETWEEN 'P00010011' AND 'P00010020' AND s.section_code = 'SEC-02';
UPDATE mon_point p
JOIN prj_section s ON s.tunnel_id = p.tunnel_id
SET p.section_id = s.id
WHERE p.point_code BETWEEN 'P00010021' AND 'P00010030' AND s.section_code = 'SEC-03';

-- ---------- 3. 样本数据（近 2 小时，30s/点，正弦漂移值 2.0~4.5 %VOL；数字交叉连接生成 0~239） ----------
INSERT INTO data_sample (point_id, ts, value, quality, source, receive_time)
SELECT p.id,
       DATE_SUB(NOW(3), INTERVAL (239 - n.num) * 30 SECOND) AS ts,
       ROUND(3.2 + 1.3 * SIN((n.num + p.id % 10) * 0.35), 2) AS value,
       0, 3, NOW(3)
FROM (
    SELECT a.n + b.n * 10 + c.n * 100 AS num
    FROM (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
    CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b
    CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2) c
) n
CROSS JOIN (SELECT id FROM mon_point WHERE point_code BETWEEN 'P00010001' AND 'P00010030') p
WHERE n.num < 240
ON DUPLICATE KEY UPDATE value = VALUES(value), quality = VALUES(quality);

-- ---------- 4. 最新值（与样本末值一致口径：取各自最新样本覆盖） ----------
INSERT INTO data_point_latest (point_id, value, ts, quality, source)
SELECT s.point_id, s.value, s.ts, s.quality, s.source
FROM data_sample s
JOIN (SELECT point_id, MAX(ts) AS max_ts
      FROM data_sample
      WHERE point_id IN (SELECT id FROM mon_point WHERE point_code BETWEEN 'P00010001' AND 'P00010030')
      GROUP BY point_id) m ON m.point_id = s.point_id AND m.max_ts = s.ts
ON DUPLICATE KEY UPDATE value = VALUES(value), ts = VALUES(ts), quality = VALUES(quality), source = VALUES(source);

-- ---------- 5. 预警事件（状态机全状态覆盖；event_no 防重） ----------
SET @tunnel_id = (SELECT id FROM prj_tunnel WHERE tunnel_code = 'T2-T1');
SET @sec_id    = (SELECT id FROM prj_section WHERE tunnel_id = @tunnel_id AND section_code = 'SEC-02');
SET @p1        = (SELECT id FROM mon_point WHERE point_code = 'P00010011');
SET @p2        = (SELECT id FROM mon_point WHERE point_code = 'P00010012');
SET @p3        = (SELECT id FROM mon_point WHERE point_code = 'P00010013');

-- E1 待确认（红级，T+5min 内，超时升级演示）
INSERT INTO warn_event (event_no, tunnel_id, section_id, point_id, hazard_type, item_type,
                        warn_level, warn_title, warn_content, trigger_value, trigger_time, warn_status)
SELECT CONCAT(DATE_FORMAT(NOW(), '%y%m%d'), '990001'), @tunnel_id, @sec_id, @p1, 3, 301, 4,
       '瓦斯浓度红色预警', '洞身段 P00010011 瓦斯浓度 4.82 %VOL，超红色阈值（4.5）', '4.82 %VOL',
       DATE_SUB(NOW(), INTERVAL 3 MINUTE), 1
WHERE NOT EXISTS (SELECT 1 FROM warn_event WHERE event_no = CONCAT(DATE_FORMAT(NOW(), '%y%m%d'), '990001'));

-- E2 已确认（橙级，已确认未派单）
INSERT INTO warn_event (event_no, tunnel_id, section_id, point_id, hazard_type, item_type,
                        warn_level, warn_title, warn_content, trigger_value, trigger_time, warn_status,
                        confirm_user_id, confirm_time, confirm_result)
SELECT CONCAT(DATE_FORMAT(NOW(), '%y%m%d'), '990002'), @tunnel_id, @sec_id, @p2, 3, 301, 3,
       '瓦斯浓度橙色预警', '洞身段 P00010012 瓦斯浓度 3.95 %VOL，超橙色阈值（3.5）', '3.95 %VOL',
       DATE_SUB(NOW(), INTERVAL 2 HOUR), 2,
       1, DATE_SUB(NOW(), INTERVAL 1 HOUR), '已电话通知洞内班组撤离，持续观察'
WHERE NOT EXISTS (SELECT 1 FROM warn_event WHERE event_no = CONCAT(DATE_FORMAT(NOW(), '%y%m%d'), '990002'));

-- E3 处置中（黄级）
INSERT INTO warn_event (event_no, tunnel_id, section_id, point_id, hazard_type, item_type,
                        warn_level, warn_title, warn_content, trigger_value, trigger_time, warn_status,
                        confirm_user_id, confirm_time, confirm_result)
SELECT CONCAT(DATE_FORMAT(NOW(), '%y%m%d'), '990003'), @tunnel_id, @sec_id, @p3, 3, 301, 2,
       '瓦斯浓度黄色预警', '洞身段 P00010013 瓦斯浓度 2.95 %VOL，超黄色阈值（2.5）', '2.95 %VOL',
       DATE_SUB(NOW(), INTERVAL 5 HOUR), 3,
       1, DATE_SUB(NOW(), INTERVAL 4 HOUR), '加强通风，已派工单处置'
WHERE NOT EXISTS (SELECT 1 FROM warn_event WHERE event_no = CONCAT(DATE_FORMAT(NOW(), '%y%m%d'), '990003'));

-- E4 待复核（蓝级，处置完成待复核）
INSERT INTO warn_event (event_no, tunnel_id, section_id, point_id, hazard_type, item_type,
                        warn_level, warn_title, warn_content, trigger_value, trigger_time, warn_status,
                        confirm_user_id, confirm_time, confirm_result)
SELECT CONCAT(DATE_FORMAT(NOW(), '%y%m%d'), '990004'), @tunnel_id, @sec_id, @p1, 3, 301, 1,
       '瓦斯浓度蓝色预警', '洞身段 P00010011 瓦斯浓度 2.25 %VOL，超蓝色阈值（2.0）', '2.25 %VOL',
       DATE_SUB(NOW(), INTERVAL 10 HOUR), 4,
       1, DATE_SUB(NOW(), INTERVAL 9 HOUR), '通风后浓度回落，待技术复核消警'
WHERE NOT EXISTS (SELECT 1 FROM warn_event WHERE event_no = CONCAT(DATE_FORMAT(NOW(), '%y%m%d'), '990004'));

-- E5 已消警（含复核人+原因，历史归档演示）
INSERT INTO warn_event (event_no, tunnel_id, section_id, point_id, hazard_type, item_type,
                        warn_level, warn_title, warn_content, trigger_value, trigger_time, warn_status,
                        confirm_user_id, confirm_time, confirm_result,
                        close_user_id, close_time, close_reason)
SELECT CONCAT(DATE_FORMAT(NOW(), '%y%m%d'), '990005'), @tunnel_id, @sec_id, @p2, 3, 301, 3,
       '瓦斯浓度橙色预警', '洞身段 P00010012 瓦斯浓度 3.70 %VOL，超橙色阈值（3.5）', '3.70 %VOL',
       DATE_SUB(NOW(), INTERVAL 2 DAY), 5,
       1, DATE_SUB(NOW(), INTERVAL 2 DAY) + INTERVAL 1 HOUR, '核实为传感器标定漂移',
       1, DATE_SUB(NOW(), INTERVAL 1 DAY), '传感器重新标定，数据连续 12h 正常，复核通过'
WHERE NOT EXISTS (SELECT 1 FROM warn_event WHERE event_no = CONCAT(DATE_FORMAT(NOW(), '%y%m%d'), '990005'));

-- E6 误报关闭
INSERT INTO warn_event (event_no, tunnel_id, section_id, point_id, hazard_type, item_type,
                        warn_level, warn_title, warn_content, trigger_value, trigger_time, warn_status,
                        confirm_user_id, confirm_time, confirm_result)
SELECT CONCAT(DATE_FORMAT(NOW(), '%y%m%d'), '990006'), @tunnel_id, @sec_id, @p3, 3, 301, 2,
       '瓦斯浓度黄色预警', '洞身段 P00010013 瓦斯浓度 2.62 %VOL，超黄色阈值（2.5）', '2.62 %VOL',
       DATE_SUB(NOW(), INTERVAL 3 DAY), 6,
       1, DATE_SUB(NOW(), INTERVAL 3 DAY) + INTERVAL 1 HOUR, '放炮作业短时扰动，非地质异常，登记误报'
WHERE NOT EXISTS (SELECT 1 FROM warn_event WHERE event_no = CONCAT(DATE_FORMAT(NOW(), '%y%m%d'), '990006'));

-- ---------- 6. 时间线（E1 完整链路演示：生成→通知→确认→派单→反馈→待复核→消警） ----------
SET @e1 = (SELECT id FROM warn_event WHERE event_no = CONCAT(DATE_FORMAT(NOW(), '%y%m%d'), '990001'));
SET @e5 = (SELECT id FROM warn_event WHERE event_no = CONCAT(DATE_FORMAT(NOW(), '%y%m%d'), '990005'));

INSERT INTO warn_event_timeline (event_id, node_type, actor_type, actor_id, actor_name, action, detail, occur_time)
SELECT @e1, 1, 1, NULL, NULL, '预警生成', '瓦斯浓度 4.82 %VOL 触发红色规则', DATE_SUB(NOW(), INTERVAL 3 MINUTE)
WHERE @e1 IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM warn_event_timeline WHERE event_id = @e1 AND node_type = 1);
INSERT INTO warn_event_timeline (event_id, node_type, actor_type, actor_id, actor_name, action, detail, occur_time)
SELECT @e1, 2, 1, NULL, NULL, '通知发送', '短信+站内消息已触达值班调度', DATE_SUB(NOW(), INTERVAL 3 MINUTE)
WHERE @e1 IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM warn_event_timeline WHERE event_id = @e1 AND node_type = 2);

INSERT INTO warn_event_timeline (event_id, node_type, actor_type, actor_id, actor_name, action, detail, occur_time)
SELECT @e5, 1, 1, NULL, NULL, '预警生成', '瓦斯浓度 3.70 %VOL 触发橙色规则', DATE_SUB(NOW(), INTERVAL 2 DAY)
WHERE @e5 IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM warn_event_timeline WHERE event_id = @e5 AND node_type = 1);
INSERT INTO warn_event_timeline (event_id, node_type, actor_type, actor_id, actor_name, action, detail, occur_time)
SELECT @e5, 10, 2, 1, '系统管理员', '复核消警', '传感器重新标定，数据连续 12h 正常，复核通过', DATE_SUB(NOW(), INTERVAL 1 DAY)
WHERE @e5 IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM warn_event_timeline WHERE event_id = @e5 AND node_type = 10);

-- ---------- 7. 模板2 + 计划2（生成任务幂等预检"跳过 2 条"口径） ----------
INSERT INTO patrol_template (template_no, template_name, version, status, remark)
SELECT 'TPL260928000002', '瓦斯专项巡检模板', 1, 1, 'W8 演示模板2'
WHERE NOT EXISTS (SELECT 1 FROM patrol_template WHERE template_no = 'TPL260928000002');

SET @tpl2_id = (SELECT id FROM patrol_template WHERE template_no = 'TPL260928000002');
INSERT INTO patrol_template_item (template_id, item_name, check_content, judge_standard, sort)
SELECT * FROM (
    SELECT @tpl2_id, '瓦斯浓度复测', '便携仪+固定式双路复测瓦斯浓度', '双路差值<0.2%', 0
    UNION ALL SELECT @tpl2_id, '通风设施',   '检查风机运行与风筒完好性',   '风机正常风筒无破损', 1
    UNION ALL SELECT @tpl2_id, '传感器状态', '检查传感器标定有效期与显示', '标定在有效期显示正常', 2
) v
WHERE NOT EXISTS (SELECT 1 FROM patrol_template_item WHERE template_id = @tpl2_id);

INSERT INTO patrol_plan (plan_no, plan_name, tunnel_id, frequency_type, time_slot, template_id, inspector_id, enabled)
SELECT 'PLN260928000002', 'T2隧道瓦斯专项复核', @tunnel_id, 2, '08:00', @tpl2_id, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM patrol_plan WHERE plan_no = 'PLN260928000002');

-- ---------- 8. 当日巡检任务（1已完成 + 1逾期；再次执行"生成任务"提示跳过 2 条） ----------
SET @plan1_id = (SELECT id FROM patrol_plan WHERE plan_no = 'PLN260928000001');
SET @plan2_id = (SELECT id FROM patrol_plan WHERE plan_no = 'PLN260928000002');
SET @today08  = TIMESTAMP(DATE(NOW()), '08:00:00');

INSERT INTO patrol_task (task_no, plan_id, tunnel_id, section_id, inspector_id, template_id, plan_time, status, finish_time)
SELECT CONCAT('TSK', DATE_FORMAT(NOW(), '%y%m%d'), '0001'), @plan1_id, @tunnel_id, @sec_id,
       1, (SELECT template_id FROM patrol_plan WHERE id = @plan1_id), @today08, 3, DATE_SUB(NOW(), INTERVAL 2 HOUR)
WHERE @plan1_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM patrol_task WHERE plan_id = @plan1_id AND plan_time = @today08);

INSERT INTO patrol_task (task_no, plan_id, tunnel_id, section_id, inspector_id, template_id, plan_time, status)
SELECT CONCAT('TSK', DATE_FORMAT(NOW(), '%y%m%d'), '0002'), @plan2_id, @tunnel_id, @sec_id,
       1, (SELECT template_id FROM patrol_plan WHERE id = @plan2_id), @today08, 4
WHERE @plan2_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM patrol_task WHERE plan_id = @plan2_id AND plan_time = @today08);

-- 已完成任务填报记录（模板1 五项；涌水突水项为异常，作为隐患来源）
SET @task_a = (SELECT id FROM patrol_task WHERE task_no = CONCAT('TSK', DATE_FORMAT(NOW(), '%y%m%d'), '0001'));
INSERT INTO patrol_record (task_id, client_key, item_id, item_name, judge_standard_snapshot, result, description, record_time, recorder_id)
SELECT @task_a, CONCAT('demo-rec-', DATE_FORMAT(NOW(), '%y%m%d'), '-', i.sort), i.id, i.item_name, i.judge_standard,
       CASE i.sort WHEN 1 THEN 2 ELSE 1 END,
       CASE i.sort WHEN 1 THEN '衬砌局部滴漏，疑似渗水点，已标记待复查' ELSE '检查正常' END,
       DATE_SUB(NOW(), INTERVAL 2 HOUR), 1
FROM patrol_template_item i
WHERE i.template_id = (SELECT template_id FROM patrol_plan WHERE id = @plan1_id)
  AND @task_a IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM patrol_record WHERE task_id = @task_a AND item_id = i.id);

-- ---------- 9. 已闭环隐患（来源=巡检记录异常项） ----------
SET @rec_anomaly = (SELECT r.id FROM patrol_record r
                    JOIN patrol_template_item i ON i.id = r.item_id
                    WHERE r.task_id = @task_a AND i.sort = 1 LIMIT 1);
INSERT INTO patrol_hazard (hazard_no, tunnel_id, section_id, source, title, description, hazard_level,
                           task_id, record_id, hazard_type, discover_user_id, discover_time,
                           handler_id, status, close_time, close_remark)
SELECT CONCAT('HZ', DATE_FORMAT(NOW(), '%y%m%d'), '0001'), @tunnel_id, @sec_id,
       1, '洞身段衬砌渗漏水隐患', '巡检发现 K0+800 处衬砌局部滴漏，渗水量约 2L/min，疑似施工缝防水失效', 2,
       @task_a, @rec_anomaly, 2, 1, DATE_SUB(NOW(), INTERVAL 2 HOUR),
       1, 3, DATE_SUB(NOW(), INTERVAL 1 HOUR), '已注浆封堵并复查无渗漏，隐患闭环'
WHERE NOT EXISTS (SELECT 1 FROM patrol_hazard WHERE hazard_no = CONCAT('HZ', DATE_FORMAT(NOW(), '%y%m%d'), '0001'));

-- ---------- 10. 处置任务（驾驶舱闭环率口径：1已完成 + 1处置中） ----------
SET @e3 = (SELECT id FROM warn_event WHERE event_no = CONCAT(DATE_FORMAT(NOW(), '%y%m%d'), '990003'));
INSERT INTO warn_dispose_task (task_no, event_id, assignee_id, assigner_id, measure, deadline, status, finish_time, create_time)
SELECT CONCAT('DP', DATE_FORMAT(NOW(), '%y%m%d'), '0001'), @e5, 1, 1, '传感器重新标定并比对 12h 数据',
       DATE_SUB(NOW(), INTERVAL 20 HOUR), 3, DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_SUB(NOW(), INTERVAL 1 DAY)
WHERE @e5 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM warn_dispose_task WHERE task_no = CONCAT('DP', DATE_FORMAT(NOW(), '%y%m%d'), '0001'));

INSERT INTO warn_dispose_task (task_no, event_id, assignee_id, assigner_id, measure, deadline, status, finish_time, create_time)
SELECT CONCAT('DP', DATE_FORMAT(NOW(), '%y%m%d'), '0002'), @e3, 1, 1, '加强通风并持续监测瓦斯浓度',
       DATE_ADD(NOW(), INTERVAL 2 HOUR), 2, NULL, DATE_SUB(NOW(), INTERVAL 4 HOUR)
WHERE @e3 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM warn_dispose_task WHERE task_no = CONCAT('DP', DATE_FORMAT(NOW(), '%y%m%d'), '0002'));

-- ---------- 11. 近 7 日报表聚合（日/周/月报与驾驶舱趋势图数据源；uk_tunnel_date upsert） ----------
INSERT INTO rpt_stat_daily (tunnel_id, stat_date, warn_total, warn_red, warn_confirmed, warn_closed,
                            dispose_total, dispose_closed, patrol_total, patrol_done,
                            hazard_new, hazard_closed, sample_count, sample_expect)
SELECT @tunnel_id, DATE_SUB(CURDATE(), INTERVAL n.d DAY),
       2 + MOD(n.d, 4), MOD(n.d, 2), 1 + MOD(n.d, 3), MOD(n.d, 3),
       1 + MOD(n.d, 2), MOD(n.d, 2), 2, 2 - MOD(n.d, 2),
       MOD(n.d, 2), MOD(n.d + 1, 2), 7000 - MOD(n.d, 5) * 300, 7200
FROM (SELECT 0 AS d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
      UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6) n
WHERE @tunnel_id IS NOT NULL
ON DUPLICATE KEY UPDATE warn_total = VALUES(warn_total), warn_red = VALUES(warn_red),
    warn_confirmed = VALUES(warn_confirmed), warn_closed = VALUES(warn_closed),
    dispose_total = VALUES(dispose_total), dispose_closed = VALUES(dispose_closed),
    patrol_total = VALUES(patrol_total), patrol_done = VALUES(patrol_done),
    hazard_new = VALUES(hazard_new), hazard_closed = VALUES(hazard_closed),
    sample_count = VALUES(sample_count), sample_expect = VALUES(sample_expect);
