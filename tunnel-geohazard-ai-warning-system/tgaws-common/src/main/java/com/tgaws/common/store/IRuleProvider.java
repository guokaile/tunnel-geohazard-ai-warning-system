package com.tgaws.common.store;

import com.tgaws.common.rule.MonRule;

import java.util.List;

/**
 * 启用规则提供契约（判定编排输入；实现位于 business，查 mon_rule。
 * 本期按测项匹配：item_type=0（全测项）或 =指定测项 的启用规则）。
 */
public interface IRuleProvider {

    /** 查询某测项匹配的启用规则（含全测项规则） */
    List<MonRule> enabledRulesByItemType(int itemType);
}
