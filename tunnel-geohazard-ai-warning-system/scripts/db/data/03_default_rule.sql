-- =====================================================================
-- TGAWS 默认规则集（占位基线）
-- 重要：以下阈值为《5.算法设计说明书》附录B"建议基线（占位示例）"，
--       必须按地质分区/围岩等级由技术专家评审定稿后更新 expression_json；
--       系统全参数化，即改即用，无需发版。
-- 规则类型：1阈值上限 2阈值下限 3速率 4突变 5组合；级别：1蓝 2黄 3橙 4红
-- =====================================================================

INSERT IGNORE INTO `mon_rule`
    (`rule_code`, `rule_name`, `hazard_type`, `item_type`, `stage`, `section_id`,
     `rule_type`, `warn_level`, `expression_json`, `priority`, `version`, `remark`) VALUES
-- 瓦斯 CH4 浓度三级阈值（TB 10120 惯例占位，待评审定稿）
('R-CH4-HI-Y', 'CH4浓度超限-黄', 3, 301, 0, 0, 1, 2,
 '{"threshold":0.5,"on":3,"hysteresisPct":5}', 100, 1, '占位基线：0.5%VOL 黄级，评审定稿'),
('R-CH4-HI-O', 'CH4浓度超限-橙', 3, 301, 0, 0, 1, 3,
 '{"threshold":1.0,"on":3,"hysteresisPct":5}', 100, 1, '占位基线：1.0%VOL 橙级（断电撤人），评审定稿'),
('R-CH4-HI-R', 'CH4浓度超限-红', 3, 301, 0, 0, 1, 4,
 '{"threshold":1.5,"on":3,"hysteresisPct":5}', 100, 1, '占位基线：1.5%VOL 红级（停止作业），评审定稿'),
-- CH4 上升速率
('R-CH4-RATE-Y', 'CH4上升速率-黄', 3, 301, 0, 0, 3, 2,
 '{"rate":0.1,"windowMin":5,"unit":"pctPerMin","on":3}', 100, 1, '占位基线：0.1%/min，评审定稿'),
-- 收敛位移速率（围岩等级相关，占位）
('R-CONV-RATE-Y', '收敛速率-黄', 6, 601, 0, 0, 3, 2,
 '{"rate":3.0,"windowHour":24,"unit":"mmPerDay","on":3}', 100, 1, '占位基线：3mm/d，按围岩等级评审定稿'),
('R-CONV-RATE-O', '收敛速率-橙', 6, 601, 0, 0, 3, 3,
 '{"rate":5.0,"windowHour":24,"unit":"mmPerDay","on":3}', 100, 1, '占位基线：5mm/d，按围岩等级评审定稿'),
-- 拱顶下沉速率
('R-SETT-RATE-Y', '下沉速率-黄', 5, 502, 0, 0, 3, 2,
 '{"rate":2.0,"windowHour":24,"unit":"mmPerDay","on":3}', 100, 1, '占位基线：2mm/d，评审定稿'),
('R-SETT-RATE-O', '下沉速率-橙', 5, 502, 0, 0, 3, 3,
 '{"rate":4.0,"windowHour":24,"unit":"mmPerDay","on":3}', 100, 1, '占位基线：4mm/d，评审定稿'),
-- 涌水量突变（CUSUM 检出即黄级起评）
('R-WATER-CUSUM-Y', '涌水量突变-黄', 2, 201, 0, 0, 4, 2,
 '{"kSigma":0.5,"hSigma":5.0,"windowHour":24}', 100, 1, 'CUSUM 参数按算法文档 5.4.3'),
-- 泥水流量突变
('R-MUD-CUSUM-Y', '泥水流量突变-黄', 4, 401, 0, 0, 4, 2,
 '{"kSigma":0.5,"hSigma":5.0,"windowHour":24}', 100, 1, 'CUSUM 参数按算法文档 5.4.3');
