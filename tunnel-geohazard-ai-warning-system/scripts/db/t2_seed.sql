-- =====================================================================
-- T2 压测种子数据（仅 tgaws_test 测试库）：
--   隧道 T2-T1 + 1000 点位（P00010001~P00011000）+ 网关 GW001 + 第三方应用 GAS-T2
-- ⚠ 仓库零密钥/零密文：网关 secret 与第三方 app_secret 的密文需以本地
--   CONFIG_ENC_KEY 经 CryptoUtil.encrypt(明文, 密钥) 生成后回填占位符（见 README）。
-- =====================================================================

INSERT INTO prj_tunnel (tunnel_code, tunnel_name, tunnel_type, stage, status)
SELECT 'T2-T1', 'T2压测隧道', 1, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM prj_tunnel WHERE tunnel_code = 'T2-T1');

-- 1000 点位（数字交叉连接技巧生成 1~1000）
INSERT INTO mon_point (point_code, point_name, tunnel_id, hazard_type, item_type, unit, collect_freq_sec, protocol)
SELECT CONCAT('P0001', LPAD(n.num, 4, '0')),
       CONCAT('T2点位', n.num),
       (SELECT id FROM prj_tunnel WHERE tunnel_code = 'T2-T1'),
       3, 301, '%VOL', 30, 1
FROM (
    SELECT a.n + b.n * 10 + c.n * 100 + 1 AS num
    FROM (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
    CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b
    CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c
    LIMIT 1000
) n
WHERE NOT EXISTS (SELECT 1 FROM mon_point WHERE point_code = 'P00011000');

-- 网关 GW001（secret 为占位符：以本地 CONFIG_ENC_KEY 经 CryptoUtil.encrypt 生成密文后回填）
INSERT INTO mon_gateway (gateway_code, gateway_name, secret, protocol, scale, status)
SELECT 'GW001', 'T2压测网关', '__REGENERATE_WITH_CRYPTOUTIL__', 1, 4, 1
WHERE NOT EXISTS (SELECT 1 FROM mon_gateway WHERE gateway_code = 'GW001');

-- =====================================================================
-- T-606 巡检域种子（仅 tgaws_test）：
--   模板 1 个（含 5 巡检项）+ 计划 1 个（每日 08:00，巡检人 admin id=1）
--   供巡检任务生成器/离线补传/隐患闭环联调用
-- =====================================================================

INSERT INTO patrol_template (template_no, template_name, version, status, remark)
SELECT 'TPL260928000001', 'T2隧道标准巡检模板', 1, 1, 'T2 种子模板'
WHERE NOT EXISTS (SELECT 1 FROM patrol_template WHERE template_no = 'TPL260928000001');

SET @tpl_id = (SELECT id FROM patrol_template WHERE template_no = 'TPL260928000001');
INSERT INTO patrol_template_item (template_id, item_name, check_content, judge_standard, sort)
SELECT * FROM (
    SELECT @tpl_id, '拱顶',     '目视检查拱顶有无掉块、裂缝', '无掉块无新增裂缝', 0
    UNION ALL SELECT @tpl_id, '涌水突水', '检查衬砌渗漏水情况',   '无滴漏线状水',     1
    UNION ALL SELECT @tpl_id, '瓦斯浓度', '便携仪实测瓦斯浓度',   '浓度<0.5%',        2
    UNION ALL SELECT @tpl_id, '初支收敛', '读取收敛计读数',       '收敛速率<2mm/d',   3
    UNION ALL SELECT @tpl_id, '排水沟',   '检查排水沟畅通情况',   '无堵塞积水',       4
) v
WHERE NOT EXISTS (SELECT 1 FROM patrol_template_item WHERE template_id = @tpl_id);

INSERT INTO patrol_plan (plan_no, plan_name, tunnel_id, frequency_type, time_slot,
                         template_id, inspector_id, enabled)
SELECT 'PLN260928000001', 'T2隧道每日巡检', (SELECT id FROM prj_tunnel WHERE tunnel_code = 'T2-T1'),
       2, '08:00', @tpl_id, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM patrol_plan WHERE plan_no = 'PLN260928000001');

-- =====================================================================
-- T-702 Open API 种子（仅 tgaws_test）：
--   第三方应用 GAS-T2（app_secret 为占位符：同上以本地 CONFIG_ENC_KEY 生成密文后回填）
-- =====================================================================
INSERT IGNORE INTO sys_third_app (app_key, app_secret, app_name, enabled)
VALUES ('GAS-T2', '__REGENERATE_WITH_CRYPTOUTIL__',
        'T2测试瓦斯监控', 1);
