package com.tgaws.business.warn.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgaws.business.mon.entity.RuleEntity;
import com.tgaws.business.mon.mapper.RuleMapper;
import com.tgaws.common.exception.BizException;
import com.tgaws.common.result.ErrorCode;
import com.tgaws.common.util.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 预警规则管理（T-708，API-C05~C10）：
 *
 * <ul>
 *   <li><b>版本化</b>：修改=同 rule_code 新版本行（version+1），历史版本留痕
 *       （P1-14 规则变更留痕；判定链路经 selectEnabledByItemType 只取启用规则，
 *       由版本行 id 引用——旧版本行保留不影响判定）；</li>
 *   <li><b>删除守卫</b>：被预警事件引用禁止删除（B0204）；</li>
 *   <li><b>表达式校验</b>：expression_json 必须为合法 JSON（Aviator 表达式
 *       在引擎内编译，规则录入仅校验结构）。</li>
 * </ul>
 */
@Service
public class RuleService {

    private static final Logger log = LoggerFactory.getLogger(RuleService.class);

    private static final ZoneId DB_ZONE = ZoneId.of("Asia/Shanghai");

    private final RuleMapper ruleMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SnowflakeIdGenerator idGenerator = SnowflakeIdGenerator.defaultInstance();

    public RuleService(RuleMapper ruleMapper) {
        this.ruleMapper = ruleMapper;
    }

    /** API-C06 新增规则（版本 1） */
    @Transactional
    public long create(RuleCmd cmd) {
        validate(cmd);
        RuleEntity entity = toEntity(cmd);
        entity.setRuleCode("RL" + LocalDate.now(DB_ZONE).format(DateTimeFormatter.ofPattern("yyMMdd"))
                + String.format("%06d", idGenerator.nextId() % 1_000_000L));
        entity.setVersion(1);
        entity.setStatus(1);
        ruleMapper.insert(entity);
        log.info("规则创建：{} v1（类型 {} 定级 {}）", entity.getRuleCode(), cmd.ruleType(), cmd.warnLevel());
        return entity.getId();
    }

    /** API-C07 修改规则（版本+1 新行，历史版本留痕） */
    @Transactional
    public long update(long id, RuleCmd cmd) {
        RuleEntity current = requireRule(id);
        validate(cmd);
        RuleEntity next = toEntity(cmd);
        next.setRuleCode(current.getRuleCode());
        next.setVersion(current.getVersion() + 1);
        next.setStatus(current.getStatus());
        ruleMapper.insert(next);
        log.info("规则改版：{} v{}→v{}", current.getRuleCode(), current.getVersion(), next.getVersion());
        return next.getId();
    }

    /** API-C08 删除规则：被预警事件引用禁止（B0204） */
    @Transactional
    public void delete(long id) {
        RuleEntity current = requireRule(id);
        if (ruleMapper.countEventRefs(id) > 0) {
            throw new BizException(ErrorCode.B0204, "规则 " + current.getRuleCode()
                    + " 被预警事件引用，禁止删除（可停用）");
        }
        ruleMapper.deleteByCode(current.getRuleCode());
        log.info("规则删除：{}（全部 {} 个版本）", current.getRuleCode(), current.getVersion());
    }

    /** API-C09 启停 */
    @Transactional
    public void updateStatus(long id, int status) {
        requireRule(id);
        if (status != 0 && status != 1) {
            throw new BizException(ErrorCode.A0002, "规则状态必须为 0停用/1启用");
        }
        ruleMapper.updateStatus(id, status);
    }

    /** API-C05 规则列表（每规则最新版本） */
    public List<RuleEntity> list(Integer hazardType, Integer itemType, Integer status) {
        return ruleMapper.selectLatestList(hazardType, itemType, status);
    }

    /** API-C10 规则版本历史 */
    public List<RuleEntity> history(long id) {
        RuleEntity current = requireRule(id);
        return ruleMapper.selectHistory(current.getRuleCode());
    }

    private RuleEntity requireRule(long id) {
        RuleEntity rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BizException(ErrorCode.B0202);
        }
        return rule;
    }

    private void validate(RuleCmd cmd) {
        if (cmd.ruleName() == null || cmd.ruleName().isBlank()) {
            throw new BizException(ErrorCode.A0002, "规则名称必填");
        }
        if (cmd.ruleType() == null || cmd.ruleType() < 1 || cmd.ruleType() > 5) {
            throw new BizException(ErrorCode.A0002, "规则类型必须为 1阈值上限/2阈值下限/3速率/4突变/5组合");
        }
        if (cmd.warnLevel() == null || cmd.warnLevel() < 1 || cmd.warnLevel() > 4) {
            throw new BizException(ErrorCode.A0002, "命中定级必须为 1蓝/2黄/3橙/4红");
        }
        if (cmd.expressionJson() == null || cmd.expressionJson().isBlank()) {
            throw new BizException(ErrorCode.A0002, "表达式参数必填");
        }
        try {
            objectMapper.readTree(cmd.expressionJson());
        } catch (Exception e) {
            throw new BizException(ErrorCode.A0002, "expressionJson 必须为合法 JSON");
        }
    }

    private RuleEntity toEntity(RuleCmd cmd) {
        RuleEntity entity = new RuleEntity();
        entity.setRuleName(cmd.ruleName());
        entity.setHazardType(cmd.hazardType() == null ? 0 : cmd.hazardType());
        entity.setItemType(cmd.itemType() == null ? 0 : cmd.itemType());
        entity.setStage(cmd.stage() == null ? 0 : cmd.stage());
        entity.setSectionId(cmd.sectionId() == null ? 0L : cmd.sectionId());
        entity.setRuleType(cmd.ruleType());
        entity.setWarnLevel(cmd.warnLevel());
        entity.setExpressionJson(cmd.expressionJson());
        entity.setPriority(cmd.priority() == null ? 100 : cmd.priority());
        entity.setRemark(cmd.remark());
        return entity;
    }

    /** 规则命令（API-C06/C07 入参） */
    public record RuleCmd(String ruleName, Integer hazardType, Integer itemType, Integer stage,
                          Long sectionId, Integer ruleType, Integer warnLevel,
                          String expressionJson, Integer priority, String remark) {
    }
}
