package com.tgaws.business.warn.manager;

import com.tgaws.business.mon.entity.RuleEntity;
import com.tgaws.business.mon.mapper.RuleMapper;
import com.tgaws.common.exception.BizException;
import com.tgaws.common.result.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 规则管理单测（T-708 API-C05~C10：校验/版本化/删除守卫/启停/历史）。
 */
class RuleServiceTest {

    private RuleMapper ruleMapper;
    private RuleService service;

    @BeforeEach
    void setUp() {
        ruleMapper = mock(RuleMapper.class);
        org.mockito.Mockito.doAnswer(inv -> {
            RuleEntity e = inv.getArgument(0);
            e.setId(100L);
            return 1;
        }).when(ruleMapper).insert(any(RuleEntity.class));
        service = new RuleService(ruleMapper);
    }

    private RuleService.RuleCmd cmd() {
        return new RuleService.RuleCmd("瓦斯阈值", 3, 301, 0, null, 1, 3,
                "{\"upper\":0.5}", 100, null);
    }

    private RuleEntity rule(int version) {
        RuleEntity r = new RuleEntity();
        r.setId(100L);
        r.setRuleCode("RL260929000001");
        r.setVersion(version);
        r.setStatus(1);
        return r;
    }

    @Test
    void createValidatesFields() {
        assertEquals(ErrorCode.A0002, assertThrows(BizException.class, () -> service.create(
                new RuleService.RuleCmd(null, 0, 0, 0, null, 1, 3, "{}", 100, null)))
                .getErrorCode(), "规则名称必填");
        assertEquals(ErrorCode.A0002, assertThrows(BizException.class, () -> service.create(
                new RuleService.RuleCmd("x", 0, 0, 0, null, 9, 3, "{}", 100, null)))
                .getErrorCode(), "规则类型 1~5");
        assertEquals(ErrorCode.A0002, assertThrows(BizException.class, () -> service.create(
                new RuleService.RuleCmd("x", 0, 0, 0, null, 1, 9, "{}", 100, null)))
                .getErrorCode(), "定级 1~4");
        assertEquals(ErrorCode.A0002, assertThrows(BizException.class, () -> service.create(
                new RuleService.RuleCmd("x", 0, 0, 0, null, 1, 3, "not-json", 100, null)))
                .getErrorCode(), "expressionJson 必须合法 JSON");
    }

    @Test
    void createInsertsVersion1() {
        assertEquals(100L, service.create(cmd()));
        ArgumentCaptor<RuleEntity> captor = ArgumentCaptor.forClass(RuleEntity.class);
        verify(ruleMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getVersion());
        assertEquals(3, captor.getValue().getHazardType());
        assertEquals(3, captor.getValue().getWarnLevel());
    }

    @Test
    void updateCreatesNewVersionRow() {
        when(ruleMapper.selectById(100L)).thenReturn(rule(2));
        ArgumentCaptor<RuleEntity> captor = ArgumentCaptor.forClass(RuleEntity.class);
        assertEquals(100L, service.update(100L, cmd()));
        verify(ruleMapper).insert(captor.capture());
        RuleEntity next = captor.getValue();
        assertEquals(3, next.getVersion(), "修改=版本+1 新行（历史留痕）");
        assertEquals("RL260929000001", next.getRuleCode(), "同规则码跨版本");
    }

    @Test
    void deleteBlockedWhenReferenced() {
        when(ruleMapper.selectById(100L)).thenReturn(rule(1));
        when(ruleMapper.countEventRefs(100L)).thenReturn(3);
        assertEquals(ErrorCode.B0204, assertThrows(BizException.class, () ->
                service.delete(100L)).getErrorCode(), "被预警事件引用禁止删除");
        verify(ruleMapper, never()).deleteByCode(any());
    }

    @Test
    void deleteSoftDeletesAllVersions() {
        when(ruleMapper.selectById(100L)).thenReturn(rule(3));
        when(ruleMapper.countEventRefs(100L)).thenReturn(0);
        service.delete(100L);
        verify(ruleMapper).deleteByCode("RL260929000001");
    }

    @Test
    void updateStatusValidatesAndDelegates() {
        when(ruleMapper.selectById(100L)).thenReturn(rule(1));
        assertEquals(ErrorCode.A0002, assertThrows(BizException.class, () ->
                service.updateStatus(100L, 5)).getErrorCode());
        service.updateStatus(100L, 0);
        verify(ruleMapper).updateStatus(100L, 0);
    }

    @Test
    void historyReturnsVersionsByCode() {
        when(ruleMapper.selectById(100L)).thenReturn(rule(2));
        when(ruleMapper.selectHistory("RL260929000001")).thenReturn(List.of(rule(2), rule(1)));
        assertEquals(2, service.history(100L).size());
        verify(ruleMapper).selectHistory("RL260929000001");
    }

    @Test
    void listDelegatesFilters() {
        service.list(3, 301, 1);
        verify(ruleMapper).selectLatestList(eq(3), eq(301), eq(1));
    }
}
