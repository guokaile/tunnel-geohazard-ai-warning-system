package com.tgaws.business.warn.manager;

import com.tgaws.business.mon.entity.PointBasicEntity;
import com.tgaws.business.mon.mapper.PointMapper;
import com.tgaws.business.warn.entity.WarnEventEntity;
import com.tgaws.common.enums.RuleType;
import com.tgaws.common.enums.WarnLevel;
import com.tgaws.common.rule.MonRule;
import com.tgaws.common.store.IRuleProvider;
import com.tgaws.common.store.SampleRow;
import com.tgaws.compute.judge.PointJudgeManager;
import com.tgaws.compute.rule.RuleHit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 预警编排单测（business：定级取严/去重抑制/事件生成 gate_stage 独立标记——评审 3.1/3.3；
 * T-603 重构后事件生成经 WarnEventService 落时间线）。
 */
class WarnJudgeServiceTest {

    @Test
    void hitGeneratesEventWithShadowGateStage() {
        PointJudgeManager judgeManager = mock(PointJudgeManager.class);
        IRuleProvider ruleProvider = mock(IRuleProvider.class);
        WarnEventService warnEventService = mock(WarnEventService.class);
        PointMapper pointMapper = mock(PointMapper.class);

        PointBasicEntity point = new PointBasicEntity();
        point.setId(1L);
        point.setPointName("CH4测点");
        point.setTunnelId(100L);
        point.setHazardType(3);
        point.setItemType(301);
        when(pointMapper.selectBasicById(1L)).thenReturn(point);
        when(ruleProvider.enabledRulesByItemType(301)).thenReturn(List.of(
                new MonRule("R-CH4-HI-Y", RuleType.THRESHOLD_UPPER, WarnLevel.YELLOW,
                        "{\"threshold\":0.5}", 100, true)));
        when(judgeManager.onSample(any(SampleRow.class), any()))
                .thenReturn(new PointJudgeManager.Judgment(1L,
                        List.of(new RuleHit("R-CH4-HI-Y", WarnLevel.YELLOW, "值=0.6")),
                        false, false, 0D));
        when(warnEventService.hasActiveEvent(1L)).thenReturn(false);

        WarnJudgeService service = new WarnJudgeService(
                judgeManager, ruleProvider, warnEventService, pointMapper, "shadow");
        service.judgeRow(new SampleRow(1L, System.currentTimeMillis(), new BigDecimal("0.6"), 0, 1));

        ArgumentCaptor<WarnEventEntity> captor = ArgumentCaptor.forClass(WarnEventEntity.class);
        verify(warnEventService).createEvent(captor.capture(), any(String.class));
        assertEquals(1, captor.getValue().getGateStage(), "影子期事件 gate_stage=1（独立字段）");
        assertEquals(WarnLevel.YELLOW.getValue(), captor.getValue().getWarnLevel());
        assertEquals(1, captor.getValue().getWarnStatus());
    }

    @Test
    void activeEventSuppressesNewInsert() {
        PointJudgeManager judgeManager = mock(PointJudgeManager.class);
        IRuleProvider ruleProvider = mock(IRuleProvider.class);
        WarnEventService warnEventService = mock(WarnEventService.class);
        PointMapper pointMapper = mock(PointMapper.class);

        PointBasicEntity point = new PointBasicEntity();
        point.setId(1L);
        point.setPointName("P");
        point.setTunnelId(100L);
        point.setHazardType(3);
        point.setItemType(301);
        when(pointMapper.selectBasicById(1L)).thenReturn(point);
        when(ruleProvider.enabledRulesByItemType(anyInt())).thenReturn(List.of(
                new MonRule("R-CH4-HI-Y", RuleType.THRESHOLD_UPPER, WarnLevel.YELLOW, "{\"threshold\":0.5}", 100, true)));
        when(judgeManager.onSample(any(SampleRow.class), any()))
                .thenReturn(new PointJudgeManager.Judgment(1L,
                        List.of(new RuleHit("R-CH4-HI-Y", WarnLevel.YELLOW, "x")), false, false, 0D));
        when(warnEventService.hasActiveEvent(1L)).thenReturn(true);

        WarnJudgeService service = new WarnJudgeService(
                judgeManager, ruleProvider, warnEventService, pointMapper, "shadow");
        service.judgeRow(new SampleRow(1L, System.currentTimeMillis(), new BigDecimal("0.6"), 0, 1));

        verify(warnEventService, never()).createEvent(any(), any());
    }
}
