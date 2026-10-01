package com.tgaws.business.warn.manager;

import com.tgaws.business.warn.entity.DisposeTaskEntity;
import com.tgaws.business.warn.mapper.DisposeTaskMapper;
import com.tgaws.business.warn.mapper.WarnEventMapper;
import com.tgaws.common.exception.BizException;
import com.tgaws.common.result.ErrorCode;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 处置闭环单测（派单/开始/完成/超时 + 状态机与时间线节点）。
 */
class DisposeTaskServiceTest {

    private DisposeTaskMapper disposeTaskMapper;
    private WarnEventMapper warnEventMapper;
    private WarnEventService warnEventService;
    private DisposeTaskService service;

    @BeforeEach
    void setUp() {
        disposeTaskMapper = mock(DisposeTaskMapper.class);
        warnEventMapper = mock(WarnEventMapper.class);
        warnEventService = mock(WarnEventService.class);
        // 模拟 Mapper 自增主键回填
        org.mockito.Mockito.doAnswer(inv -> {
            DisposeTaskEntity e = inv.getArgument(0);
            e.setId(300L);
            return 1;
        }).when(disposeTaskMapper).insert(any(DisposeTaskEntity.class));
        service = new DisposeTaskService(disposeTaskMapper, warnEventMapper, warnEventService);
    }

    @Test
    void dispatchTransitionsEventAndWritesNode5() {
        when(warnEventMapper.updateToDisposing(1L)).thenReturn(1);
        service.dispatch(1L, 8L, 9L, "张三", "停止作业并核查渗水",
                LocalDateTime.now().plusHours(2));
        verify(disposeTaskMapper).insert(any(DisposeTaskEntity.class));
        verify(warnEventService).appendDisposeNode(eq(1L), eq(9L), eq("张三"), anyString());
    }

    @Test
    void dispatchWrongEventStateThrowsB0302() {
        when(warnEventMapper.updateToDisposing(1L)).thenReturn(0);
        assertEquals(ErrorCode.B0302, assertThrows(BizException.class, () ->
                service.dispatch(1L, 8L, 9L, "张三", "x", LocalDateTime.now()))
                .getErrorCode());
    }

    @Test
    void startWrongStateThrowsB0402() {
        DisposeTaskEntity task = new DisposeTaskEntity();
        task.setId(5L);
        task.setAssigneeId(8L);
        when(disposeTaskMapper.selectById(5L)).thenReturn(task);
        when(disposeTaskMapper.updateOnStart(5L)).thenReturn(0);
        assertEquals(ErrorCode.B0402, assertThrows(BizException.class,
                () -> service.start(5L, 8L)).getErrorCode());
    }

    @Test
    void startNotAssigneeThrowsB0403() {
        DisposeTaskEntity task = new DisposeTaskEntity();
        task.setId(5L);
        task.setAssigneeId(7L);
        when(disposeTaskMapper.selectById(5L)).thenReturn(task);
        assertEquals(ErrorCode.B0403, assertThrows(BizException.class,
                () -> service.start(5L, 8L)).getErrorCode(), "仅任务责任人可开始处置");
    }

    @Test
    void finishTransitionsToPendingReviewAndNode9() {
        DisposeTaskEntity task = new DisposeTaskEntity();
        task.setId(5L);
        task.setEventId(1L);
        when(disposeTaskMapper.selectById(5L)).thenReturn(task);
        when(disposeTaskMapper.updateOnFinish(5L)).thenReturn(1);
        when(warnEventMapper.updateToPendingReview(1L)).thenReturn(1);
        service.finish(5L, 9L, "张三");
        verify(warnEventService).appendPendingReviewNode(eq(1L), eq(9L), eq("张三"));
    }

    @Test
    void finishMissingTaskThrowsB0401() {
        when(disposeTaskMapper.selectById(99L)).thenReturn(null);
        assertEquals(ErrorCode.B0401, assertThrows(BizException.class,
                () -> service.finish(99L, 9L, "张三")).getErrorCode());
    }

    @Test
    void overdueScanDelegates() {
        when(disposeTaskMapper.markOverdue(any(LocalDateTime.class))).thenReturn(3);
        assertEquals(3, service.markOverdue());
    }

    // ==================== T-708 API-C16/C18 ====================

    @Test
    void feedbackRequiresDisposingStateAndContent() {
        DisposeTaskEntity task = new DisposeTaskEntity();
        task.setId(5L);
        task.setEventId(1L);
        task.setStatus(1);
        when(disposeTaskMapper.selectById(5L)).thenReturn(task);
        assertEquals(ErrorCode.B0402, assertThrows(BizException.class, () ->
                service.feedback(5L, 9L, "张三", "内容", null)).getErrorCode(), "仅处置中可反馈");

        task.setStatus(2);
        assertEquals(ErrorCode.A0002, assertThrows(BizException.class, () ->
                service.feedback(5L, 9L, "张三", "  ", null)).getErrorCode(), "反馈内容必填");
    }

    @Test
    void feedbackInsertsAndAppendsNode() {
        DisposeTaskEntity task = new DisposeTaskEntity();
        task.setId(5L);
        task.setEventId(1L);
        task.setStatus(2);
        when(disposeTaskMapper.selectById(5L)).thenReturn(task);
        org.mockito.Mockito.doAnswer(inv -> {
            com.tgaws.business.warn.entity.DisposeFeedbackEntity e = inv.getArgument(0);
            e.setId(77L);
            return 1;
        }).when(disposeTaskMapper).insertFeedback(any(com.tgaws.business.warn.entity.DisposeFeedbackEntity.class));
        assertEquals(77L, service.feedback(5L, 9L, "张三", "已处置", "[]"));
        verify(warnEventService).appendFeedbackNode(eq(1L), eq(9L), eq("张三"), eq("已处置"));
    }

    @Test
    void pageDelegatesWithScope() {
        when(disposeTaskMapper.selectPage(eq(8L), eq(1), any(), eq(0), eq(20)))
                .thenReturn(List.of(new DisposeTaskEntity()));
        when(disposeTaskMapper.countPage(eq(8L), eq(1), any())).thenReturn(1L);
        var page = service.page(8L, 1, 1, 20, com.tgaws.common.datascope.DataScope.all());
        assertEquals(1L, page.total());
        assertEquals(1, page.list().size());
    }
}
