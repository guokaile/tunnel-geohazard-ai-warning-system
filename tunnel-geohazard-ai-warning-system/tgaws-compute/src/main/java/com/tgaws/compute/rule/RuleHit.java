package com.tgaws.compute.rule;

import com.tgaws.common.enums.WarnLevel;

/**
 * 规则命中结果。
 *
 * @param ruleCode 规则编码
 * @param warnLevel 命中定级
 * @param detail    触发值/判定描述（预警事件 snapshot 留痕）
 */
public record RuleHit(String ruleCode, WarnLevel warnLevel, String detail) {
}
