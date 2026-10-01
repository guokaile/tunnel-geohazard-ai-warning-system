package com.tgaws.business.warn.manager;

import com.tgaws.business.warn.entity.HazardEventEntity;
import com.tgaws.business.warn.mapper.HazardEventMapper;
import com.tgaws.common.exception.BizException;
import com.tgaws.common.result.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * 灾害险情登记单测（FR-407 + 评审 3.2 event_time 三重校验 / 3.1 关联一对多）。
 */
class HazardEventServiceTest {

    private HazardEventMapper hazardEventMapper;
    private WarnEventService warnEventService;
    private HazardEventService service;

    @BeforeEach
    void setUp() {
        hazardEventMapper = mock(HazardEventMapper.class);
        warnEventService = mock(WarnEventService.class);
        org.mockito.Mockito.doAnswer(inv -> {
            HazardEventEntity e = inv.getArgument(0);
            e.setId(800L);
            return 1;
        }).when(hazardEventMapper).insert(any(HazardEventEntity.class));
        service = new HazardEventService(hazardEventMapper, warnEventService);
    }

    private HazardEventService.RegisterCmd cmd(LocalDateTime eventTime) {
        return new HazardEventService.RegisterCmd(1L, null, 3, eventTime, "K12+300",
                "瓦斯浓度突升", 2, null, null);
    }

    // ==================== 入参校验 ====================

    @Test
    void registerRequiresTunnel() {
        assertEquals(ErrorCode.A0002, assertThrows(BizException.class, () ->
                service.register(new HazardEventService.RegisterCmd(
                        null, null, 3, LocalDateTime.now(), null, null, null, null, null),
                        9L, "张三", List.of())).getErrorCode(), "隧道必填");
    }

    @Test
    void registerRejectsInvalidHazardTypeAndLevel() {
        assertEquals(ErrorCode.A0002, assertThrows(BizException.class, () -> service.register(
                new HazardEventService.RegisterCmd(1L, null, 7, LocalDateTime.now().minusHours(1),
                        null, null, null, null, null), 9L, "张三", List.of())).getErrorCode(),
                "灾害类型越界拒绝");
        assertEquals(ErrorCode.A0002, assertThrows(BizException.class, () -> service.register(
                new HazardEventService.RegisterCmd(1L, null, 3, LocalDateTime.now().minusHours(1),
                        null, null, 5, null, null), 9L, "张三", List.of())).getErrorCode(),
                "灾害级别越界拒绝");
    }

    // ==================== event_time 三重校验（评审 3.2） ====================

    @Test
    void registerRejectsNullEventTime() {
        assertEquals(ErrorCode.A0002, assertThrows(BizException.class, () ->
                service.register(cmd(null), 9L, "张三", List.of())).getErrorCode(),
                "灾害发生时间必填（模型评估真值锚点）");
    }

    @Test
    void registerRejectsFutureEventTime() {
        assertEquals(ErrorCode.A0002, assertThrows(BizException.class, () ->
                service.register(cmd(LocalDateTime.now().plusMinutes(5)), 9L, "张三", List.of()))
                .getErrorCode(), "灾害发生时间不得晚于当前时间");
    }

    @Test
    void registerRejectsTooOldEventTime() {
        assertEquals(ErrorCode.A0002, assertThrows(BizException.class, () ->
                service.register(cmd(LocalDateTime.now().minusDays(31)), 9L, "张三", List.of()))
                .getErrorCode(), "灾害发生时间不得早于当前时间 30 天");
    }

    @Test
    void registerAcceptsBoundaryEventTime() {
        // +1s 防调用方与校验方两次 now() 取时毫秒漂移把恰 30 天边界误判为越界
        service.register(cmd(LocalDateTime.now().minusDays(30).plusSeconds(1)), 9L, "张三", List.of());
        verify(hazardEventMapper).insert(any(HazardEventEntity.class));
    }

    // ==================== 关联一对多（评审 3.1） ====================

    @Test
    void registerLinksAllRelatedEvents() {
        service.register(cmd(LocalDateTime.now().minusHours(1)), 9L, "张三",
                List.of(11L, 12L, 13L));
        verify(warnEventService).linkHazard(eq(800L), eq(List.of(11L, 12L, 13L)), eq(9L), eq("张三"));
    }

    @Test
    void registerWithoutRelationsSkipsLink() {
        service.register(cmd(LocalDateTime.now().minusHours(1)), 9L, "张三", List.of());
        verify(warnEventService, never()).linkHazard(anyLong(), any(), any(), any());
    }

    @Test
    void registerStoresFirstRelationAndDefaultsLevel() {
        List<HazardEventEntity> captured = new java.util.ArrayList<>();
        org.mockito.Mockito.doAnswer(inv -> {
            HazardEventEntity e = inv.getArgument(0);
            captured.add(e);
            e.setId(801L);
            return 1;
        }).when(hazardEventMapper).insert(any(HazardEventEntity.class));
        service.register(new HazardEventService.RegisterCmd(
                1L, null, 3, LocalDateTime.now().minusHours(1), null, null, null, null, null),
                9L, "张三", List.of(21L, 22L));
        assertEquals(21L, captured.get(0).getRelateEventId(), "relate_event_id 存主关联");
        assertEquals(1, captured.get(0).getLevel(), "级别缺省为 1蓝");
        assertNull(captured.get(0).getPatrolHazardId());
    }

    // ==================== 列表（API-C22） ====================

    @Test
    void listRequiresTunnel() {
        assertEquals(ErrorCode.A0002, assertThrows(BizException.class, () ->
                service.list(null, 3, null, null)).getErrorCode(), "隧道必填");
    }

    @Test
    void listDelegatesWithFilters() {
        LocalDateTime from = LocalDateTime.now().minusDays(7);
        when(hazardEventMapper.selectByTunnel(1L, 3, from, null))
                .thenReturn(List.of(new HazardEventEntity()));
        assertEquals(1, service.list(1L, 3, from, null).size());
        verify(hazardEventMapper).selectByTunnel(eq(1L), eq(3), eq(from), isNull());
    }
}
