package com.tgaws.business.warn.manager;

import com.tgaws.business.mon.entity.RuleEntity;
import com.tgaws.business.mon.mapper.RuleMapper;
import com.tgaws.common.enums.RuleType;
import com.tgaws.common.enums.WarnLevel;
import com.tgaws.common.rule.MonRule;
import com.tgaws.common.store.IRuleProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * IRuleProvider 业务实现（mon_rule 查询 → 契约模型）。
 */
@Component
public class RuleProviderImpl implements IRuleProvider {

    private final RuleMapper ruleMapper;

    public RuleProviderImpl(RuleMapper ruleMapper) {
        this.ruleMapper = ruleMapper;
    }

    @Override
    public List<MonRule> enabledRulesByItemType(int itemType) {
        List<RuleEntity> entities = ruleMapper.selectEnabledByItemType(itemType);
        List<MonRule> rules = new ArrayList<>(entities.size());
        for (RuleEntity entity : entities) {
            rules.add(new MonRule(
                    entity.getRuleCode(),
                    RuleType.of(entity.getRuleType()),
                    WarnLevel.of(entity.getWarnLevel()),
                    entity.getExpressionJson(),
                    entity.getPriority() == null ? 100 : entity.getPriority(),
                    entity.getStatus() != null && entity.getStatus() == 1));
        }
        return rules;
    }
}
