package com.tgaws.business.warn.manager;

import com.tgaws.business.mon.entity.PointBasicEntity;
import com.tgaws.business.mon.mapper.PointMapper;
import com.tgaws.business.warn.entity.WarnEventEntity;
import com.tgaws.common.enums.GateStage;
import com.tgaws.common.enums.RuleType;
import com.tgaws.common.enums.WarnLevel;
import com.tgaws.common.rule.MonRule;
import com.tgaws.common.store.IRuleProvider;
import com.tgaws.common.store.SampleRow;
import com.tgaws.common.util.SnowflakeIdGenerator;
import com.tgaws.compute.judge.PointJudgeManager;
import com.tgaws.compute.rule.RuleHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 预警判定编排（business，评审 3.1 职责边界：定级取严+去重抑制+事件生成在此层）：
 *
 * <p>①compute 输出判定结论 → ②三路取严定级（规则最高级 vs 突变规则级）→
 * ③B0306 去重抑制（同点位未消警事件不新建）→ ④事件生成（gate_stage 独立字段标记
 * 影子/灰度，评审 3.3）→ ⑤通知拦截点属 NotifyService（W6），算法与事件层不感知。</p>
 */
@Component
public class WarnJudgeService {

    private static final Logger log = LoggerFactory.getLogger(WarnJudgeService.class);

    private static final ZoneId DB_ZONE = ZoneId.of("Asia/Shanghai");

    private final PointJudgeManager judgeManager;
    private final IRuleProvider ruleProvider;
    private final WarnEventService warnEventService;
    private final PointMapper pointMapper;
    private final SnowflakeIdGenerator idGenerator = SnowflakeIdGenerator.defaultInstance();

    /** 门禁阶段（只影响通知拦截，不影响判定与落库——评审 3.3；W6 接 sys_config 动态化） */
    private final GateStage gateStage;

    public WarnJudgeService(PointJudgeManager judgeManager, IRuleProvider ruleProvider,
                            WarnEventService warnEventService, PointMapper pointMapper,
                            @Value("${model.gate.stage:shadow}") String gateStageConfig) {
        this.judgeManager = judgeManager;
        this.ruleProvider = ruleProvider;
        this.warnEventService = warnEventService;
        this.pointMapper = pointMapper;
        this.gateStage = GateStage.of(gateStageConfig);
    }

    /** 入库成功事件监听（评审 3.2：事务提交后驱动，非轮询） */
    @EventListener
    public void onSamplesStored(StoredSampleDispatcher.SamplesStoredEvent event) {
        for (SampleRow row : event.rows()) {
            judgeRow(row);
        }
    }

    void judgeRow(SampleRow row) {
        PointBasicEntity point = pointMapper.selectBasicById(row.pointId());
        if (point == null) {
            log.debug("点位 {} 未配置，跳过判定", row.pointId());
            return;
        }
        List<MonRule> rules = ruleProvider.enabledRulesByItemType(point.getItemType());
        if (rules.isEmpty()) {
            return;
        }
        PointJudgeManager.Judgment judgment = judgeManager.onSample(row, rules);
        WarnLevel level = judgment.highestRuleLevel().orElse(null);
        if (judgment.mutationHit()) {
            WarnLevel mutationLevel = rules.stream()
                    .filter(r -> r.ruleType() == RuleType.MUTATION)
                    .map(MonRule::warnLevel)
                    .max(Enum::compareTo)
                    .orElse(null);
            if (mutationLevel != null) {
                level = level == null ? mutationLevel : (level.compareTo(mutationLevel) >= 0 ? level : mutationLevel);
            }
        }
        if (level == null) {
            return; // 无命中
        }
        // B0306 去重抑制：同点位未消警事件存在则不新建（快照更新 W6 完善）
        if (warnEventService.hasActiveEvent(row.pointId())) {
            log.debug("命中告警抑制：点位 {} 存在未消警事件", row.pointId());
            return;
        }
        WarnEventEntity entity = buildEvent(row, point, level, judgment);
        String snapshot = buildSnapshot(row, judgment);
        warnEventService.createEvent(entity, snapshot);
        log.info("预警事件生成：{} 级别 {} 门禁 {}（通知拦截在 NotifyService，W6）",
                entity.getEventNo(), level.getLabel(), gateStage.getLabel());
    }

    private WarnEventEntity buildEvent(SampleRow row, PointBasicEntity point, WarnLevel level,
                                       PointJudgeManager.Judgment judgment) {
        WarnEventEntity entity = new WarnEventEntity();
        entity.setEventNo(LocalDate.now(DB_ZONE).format(DateTimeFormatter.ofPattern("yyMMdd"))
                + String.format("%06d", idGenerator.nextId() % 1_000_000L));
        entity.setTunnelId(point.getTunnelId());
        entity.setPointId(row.pointId());
        entity.setHazardType(point.getHazardType());
        entity.setItemType(point.getItemType());
        entity.setWarnLevel(level.getValue());
        entity.setWarnTitle(point.getPointName() + " " + level.getLabel() + "预警");
        StringBuilder content = new StringBuilder("值=").append(row.value());
        for (RuleHit hit : judgment.ruleHits()) {
            content.append("；").append(hit.ruleCode()).append(":").append(hit.detail());
        }
        if (judgment.mutationHit()) {
            content.append("；CUSUM突变 cumsum=").append(judgment.cumsum());
        }
        entity.setWarnContent(content.toString());
        entity.setWarnStatus(1);
        entity.setGateStage(mapGateStage(gateStage));
        entity.setTriggerTime(LocalDateTime.now(DB_ZONE));
        return entity;
    }

    /** 触发快照（时间线节点1 detail 留痕：升级覆盖 trigger_value 后历史仍可追溯） */
    private String buildSnapshot(SampleRow row, PointJudgeManager.Judgment judgment) {
        StringBuilder sb = new StringBuilder("值=").append(row.value());
        for (RuleHit hit : judgment.ruleHits()) {
            sb.append("；").append(hit.ruleCode()).append(":").append(hit.detail());
        }
        if (judgment.mutationHit()) {
            sb.append("；CUSUM突变 cumsum=").append(judgment.cumsum());
        }
        return sb.toString();
    }

    /** GateStage → gate_stage 字段（0 正常/1 影子/2 灰度；独立于状态机） */
    static int mapGateStage(GateStage stage) {
        return switch (stage) {
            case SHADOW -> 1;
            case GRAY -> 2;
            case LIVE -> 0;
        };
    }
}
