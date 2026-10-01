package com.tgaws.business.warn.manager;

import com.tgaws.business.warn.entity.TimelineEntity;
import com.tgaws.business.warn.entity.WarnEventEntity;
import com.tgaws.business.warn.mapper.TimelineMapper;
import com.tgaws.business.warn.mapper.WarnEventMapper;
import com.tgaws.common.exception.BizException;
import com.tgaws.common.result.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 预警事件状态机+时间线单测（T-603：条件更新并发控制/节点留痕/触发快照/灾变关联一对多）。
 */
class WarnEventServiceTest {

    private WarnEventMapper warnEventMapper;
    private TimelineMapper timelineMapper;
    private WarnEventService service;

    @BeforeEach
    void setUp() {
        warnEventMapper = mock(WarnEventMapper.class);
        timelineMapper = mock(TimelineMapper.class);
        service = new WarnEventService(warnEventMapper, timelineMapper);
    }

    @Test
    void createEventAppendsGeneratedNodeWithSnapshot() {
        WarnEventEntity entity = new WarnEventEntity();
        entity.setId(101L); // 模拟 Mapper 自增主键回填
        entity.setEventNo("260927000001");
        entity.setPointId(1L);
        entity.setWarnLevel(2);
        entity.setWarnStatus(1);
        service.createEvent(entity, "值=0.6；R-CH4-HI-Y:值=0.6");
        ArgumentCaptor<TimelineEntity> captor = ArgumentCaptor.forClass(TimelineEntity.class);
        verify(timelineMapper).insert(captor.capture());
        assertEquals(WarnEventService.NODE_GENERATED, captor.getValue().getNodeType());
        assertEquals("值=0.6；R-CH4-HI-Y:值=0.6", captor.getValue().getDetail(),
                "节点1 detail 存触发值快照（升级覆盖后仍可追溯）");
    }

    @Test
    void confirmSuccessTransitionsAndWritesNode3() {
        when(warnEventMapper.updateOnConfirm(eq(1L), eq(2), any(), any())).thenReturn(1);
        service.confirm(1L, 9L, "张三", true, "浓度超限属实");
        verify(timelineMapper).insert(any(TimelineEntity.class));
    }

    @Test
    void confirmWrongStateThrowsB0302() {
        when(warnEventMapper.updateOnConfirm(eq(1L), eq(2), any(), any())).thenReturn(0);
        assertEquals(ErrorCode.B0302, assertThrows(BizException.class,
                () -> service.confirm(1L, 9L, "张三", true, "x")).getErrorCode());
        verify(timelineMapper, never()).insert(any(TimelineEntity.class));
    }

    @Test
    void falseAlarmWritesNode4() {
        when(warnEventMapper.updateOnConfirm(eq(1L), eq(6), any(), any())).thenReturn(1);
        service.confirm(1L, 9L, "张三", false, "传感器漂移");
        ArgumentCaptor<TimelineEntity> captor = ArgumentCaptor.forClass(TimelineEntity.class);
        verify(timelineMapper).insert(captor.capture());
        assertEquals(WarnEventService.NODE_FALSE_ALARM, captor.getValue().getNodeType());
    }

    @Test
    void closeRequiresReason() {
        assertEquals(ErrorCode.B0304, assertThrows(BizException.class,
                () -> service.close(1L, 9L, "张三", "  ")).getErrorCode());
        verify(warnEventMapper, never()).updateOnClose(anyLong(), any(), any());
    }

    @Test
    void closeWritesNode10() {
        when(warnEventMapper.updateOnClose(eq(1L), any(), any())).thenReturn(1);
        service.close(1L, 9L, "张三", "浓度回落并稳定");
        ArgumentCaptor<TimelineEntity> captor = ArgumentCaptor.forClass(TimelineEntity.class);
        verify(timelineMapper).insert(captor.capture());
        assertEquals(WarnEventService.NODE_CLOSED, captor.getValue().getNodeType());
    }

    @Test
    void upgradeWritesNode7WithOldNewLevel() {
        WarnEventEntity current = new WarnEventEntity();
        current.setId(1L);
        current.setWarnLevel(2);
        when(warnEventMapper.selectById(1L)).thenReturn(current);
        when(warnEventMapper.updateLevelUp(1L, 3)).thenReturn(1);
        service.upgrade(1L, 9L, "张三", 3, "浓度持续上升");
        ArgumentCaptor<TimelineEntity> captor = ArgumentCaptor.forClass(TimelineEntity.class);
        verify(timelineMapper).insert(captor.capture());
        assertEquals(WarnEventService.NODE_UPGRADED, captor.getValue().getNodeType());
        assertEquals("级别 2→3：浓度持续上升", captor.getValue().getDetail(),
                "升级节点 detail 记 old→new（多次升降级全留痕）");
    }

    @Test
    void linkHazardWritesNode11ToAllEvents() {
        service.linkHazard(100L, List.of(1L, 2L, 3L), 9L, "李四");
        verify(warnEventMapper).updateHazardLink(1L, 100L);
        verify(warnEventMapper).updateHazardLink(2L, 100L);
        verify(warnEventMapper).updateHazardLink(3L, 100L);
        ArgumentCaptor<TimelineEntity> captor = ArgumentCaptor.forClass(TimelineEntity.class);
        verify(timelineMapper, org.mockito.Mockito.times(3)).insert(captor.capture());
        captor.getAllValues().forEach(n ->
                assertEquals(WarnEventService.NODE_HAZARD_CONFIRMED, n.getNodeType(),
                        "全部关联事件各插一条灾变确认节点（一对多，评审 4.3）"));
    }

    // ==================== T-708 API-C01/C02/C04 ====================

    @Test
    void getByIdThrowsB0301WhenMissing() {
        when(warnEventMapper.selectById(404L)).thenReturn(null);
        assertEquals(ErrorCode.B0301, assertThrows(BizException.class, () ->
                service.getById(404L)).getErrorCode());
    }

    @Test
    void pageDelegatesWithScopeCondition() {
        when(warnEventMapper.selectPage(eq(4L), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), any(), eq(0), eq(20)))
                .thenReturn(List.of(new WarnEventEntity()));
        when(warnEventMapper.countPage(eq(4L), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), any())).thenReturn(1L);
        var page = service.page(4L, null, null, null, null, null, null, 1, 20,
                com.tgaws.common.datascope.DataScope.ofTunnels(java.util.Set.of(4L)));
        assertEquals(1L, page.total());
        assertEquals(1, page.list().size());
    }

    @Test
    void statsRequiresRange() {
        assertEquals(ErrorCode.A0002, assertThrows(BizException.class, () ->
                service.stats(null, null, com.tgaws.common.datascope.DataScope.all())).getErrorCode());
    }
}
