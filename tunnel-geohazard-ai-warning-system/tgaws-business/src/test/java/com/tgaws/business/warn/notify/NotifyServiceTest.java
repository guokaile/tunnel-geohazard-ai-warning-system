package com.tgaws.business.warn.notify;

import com.tgaws.business.warn.entity.NotifyLogEntity;
import com.tgaws.business.warn.entity.WarnEventEntity;
import com.tgaws.business.warn.manager.WarnEventService;
import com.tgaws.business.warn.mapper.NotifyLogMapper;
import com.tgaws.common.enums.NotifyChannelType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Outbox 通知服务单测（评审 3.3/4.4）：
 * 门禁拦截在通知层/业务事务内落库 deliver_state=0/成功更新 delivered/
 * 失败 attempt+nextRetry 在发送后更新/达上限放弃/2 次失败转站内兜底/积压哨兵。
 */
class NotifyServiceTest {

    private NotifyLogMapper notifyLogMapper;
    private WarnEventService warnEventService;
    private NotifyService notifyService;

    private NotifyChannel innerChannel;

    @BeforeEach
    void setUp() {
        notifyLogMapper = mock(NotifyLogMapper.class);
        warnEventService = mock(WarnEventService.class);
        innerChannel = new InnerChannel();
        // 模拟 Mapper 自增主键回填（无事务测试路径直接 dispatch 需要 id）
        org.mockito.Mockito.doAnswer(inv -> {
            NotifyLogEntity e = inv.getArgument(0);
            e.setId(200L);
            return 1;
        }).when(notifyLogMapper).insert(any());
        notifyService = newService(List.of(innerChannel), 6);
    }

    /** 构造服务（mock 事务管理器 + 事务状态 stub，兼容 claimAndDispatch 的 TransactionTemplate） */
    private NotifyService newService(List<NotifyChannel> channels, int retryMax) {
        org.springframework.transaction.PlatformTransactionManager tm =
                mock(org.springframework.transaction.PlatformTransactionManager.class);
        org.springframework.transaction.TransactionStatus ts =
                mock(org.springframework.transaction.TransactionStatus.class);
        when(tm.getTransaction(any())).thenReturn(ts);
        return new NotifyService(notifyLogMapper, warnEventService, tm,
                channels, retryMax, 100, 60);
    }

    private WarnEventEntity event(int gateStage, int level) {
        WarnEventEntity entity = new WarnEventEntity();
        entity.setId(1L);
        entity.setEventNo("260928000001");
        entity.setWarnTitle("CH4测点 黄色预警");
        entity.setWarnContent("值=0.6");
        entity.setGateStage(gateStage);
        entity.setWarnLevel(level);
        return entity;
    }

    @Test
    void shadowStageSkipsNotification() {
        notifyService.notifyEventCreated(event(1, 2));
        verify(notifyLogMapper, never()).insert(any());
    }

    @Test
    void grayOrangeSkipsAutoNotify() {
        notifyService.notifyEventCreated(event(2, 3));
        verify(notifyLogMapper, never()).insert(any());
    }

    @Test
    void liveQueuesOutboxRecordsInTransaction() {
        notifyService.notifyEventCreated(event(0, 2));
        ArgumentCaptor<NotifyLogEntity> captor = ArgumentCaptor.forClass(NotifyLogEntity.class);
        verify(notifyLogMapper, times(1)).insert(captor.capture());
        assertEquals(0, captor.getValue().getDeliverState(), "业务事务内落库：deliver_state=0 待投递");
        assertEquals(NotifyChannelType.INNER.getValue(), captor.getValue().getChannelType());
    }

    @Test
    void dispatchSuccessUpdatesDelivered() {
        NotifyLogEntity record = pendingRecord(0);
        notifyService.dispatch(record);
        verify(notifyLogMapper).updateDelivered(eq(record.getId()));
    }

    @Test
    void dispatchFailureUpdatesAttemptAfterSend() {
        NotifyChannel failing = new NotifyChannel() {
            @Override
            public NotifyChannelType type() {
                return NotifyChannelType.SMS;
            }

            @Override
            public String name() {
                return "短信";
            }

            @Override
            public void send(String target, String content) {
                throw new RuntimeException("供应商超时");
            }
        };
        NotifyService smsService = newService(List.of(failing), 6);
        NotifyLogEntity record = pendingRecord(0);
        record.setChannelType(NotifyChannelType.SMS.getValue());
        smsService.dispatch(record);
        ArgumentCaptor<LocalDateTime> nextRetry = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(notifyLogMapper).updateAfterAttempt(eq(record.getId()), eq(2), eq(2), eq(1),
                nextRetry.capture(), any());
        assertNotNull(nextRetry.getValue(), "attempt 与 next_retry 在发送尝试之后更新");
        assertEquals(10L, NotifyService.backoffSeconds(1));
        assertEquals(30L, NotifyService.backoffSeconds(2));
        assertEquals(120L, NotifyService.backoffSeconds(3));
        assertEquals(600L, NotifyService.backoffSeconds(6));
    }

    @Test
    void maxAttemptsAbandoned() {
        NotifyLogEntity record = pendingRecord(6);
        notifyService.dispatch(record);
        verify(notifyLogMapper).updateAfterAttempt(eq(record.getId()), eq(2), eq(3), eq(6),
                any(), any());
    }

    @Test
    void fallbackToInnerAfterTwoFailures() {
        NotifyChannel failing = new NotifyChannel() {
            @Override
            public NotifyChannelType type() {
                return NotifyChannelType.SMS;
            }

            @Override
            public String name() {
                return "短信";
            }

            @Override
            public void send(String target, String content) {
                throw new RuntimeException("供应商超时");
            }
        };
        NotifyService smsService = newService(List.of(failing, innerChannel), 6);
        NotifyLogEntity record = pendingRecord(1);
        record.setChannelType(NotifyChannelType.SMS.getValue());
        smsService.dispatch(record);
        ArgumentCaptor<NotifyLogEntity> fallback = ArgumentCaptor.forClass(NotifyLogEntity.class);
        verify(notifyLogMapper).insert(fallback.capture());
        assertEquals(NotifyChannelType.INNER.getValue(),
                fallback.getValue().getChannelType(), "2 次失败转站内兜底");
        assertEquals(0, fallback.getValue().getDeliverState());
    }

    @Test
    void sentinelRunsWithoutError() {
        when(notifyLogMapper.countPending()).thenReturn(150);
        when(notifyLogMapper.oldestPendingTime()).thenReturn(LocalDateTime.now().minusSeconds(120));
        notifyService.sentinel(); // 不抛异常即通过（告警走 ERROR 日志）
        verify(notifyLogMapper).countPending();
    }

    private static NotifyLogEntity pendingRecord(int attemptCount) {
        NotifyLogEntity record = new NotifyLogEntity();
        record.setId(100L);
        record.setEventId(1L);
        record.setChannelType(NotifyChannelType.INNER.getValue());
        record.setTarget("值班调度");
        record.setContent("test");
        record.setAttemptCount(attemptCount);
        return record;
    }
}
