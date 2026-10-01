package com.tgaws.common.rule;

import com.tgaws.common.enums.RuleType;
import com.tgaws.common.enums.WarnLevel;

/**
 * 预警规则配置模型（mon_rule 行映射；契约层位于 common——business 提供、
 * compute 消费，跨模块强类型传递）。
 *
 * @param ruleCode       规则编码
 * @param ruleType       规则类型（阈值上限/下限/速率/突变/组合）
 * @param warnLevel      命中定级（蓝黄橙红）
 * @param expressionJson 条件参数 JSON（如 {"threshold":0.5,"on":3,"hysteresisPct":5}）
 * @param priority       优先级（同级命中取优先级高者）
 * @param enabled        是否启用
 */
public record MonRule(
        String ruleCode,
        RuleType ruleType,
        WarnLevel warnLevel,
        String expressionJson,
        int priority,
        boolean enabled
) {
}
