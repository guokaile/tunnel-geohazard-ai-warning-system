package com.tgaws.web.controller;

import com.tgaws.business.warn.entity.DisposeTaskEntity;
import com.tgaws.business.warn.entity.WarnEventEntity;
import com.tgaws.business.warn.manager.DisposeTaskService;
import com.tgaws.business.warn.manager.RuleService;
import com.tgaws.business.warn.manager.WarnEventService;
import com.tgaws.business.warn.notify.NotifyService;
import com.tgaws.common.datascope.DataScope;
import com.tgaws.common.result.PageResult;
import com.tgaws.web.config.GlobalExceptionHandler;
import com.tgaws.web.security.LoginContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 预警中心控制器单测（T-708 API-C01~C20 关键路径，standalone MockMvc）。
 */
class WarnControllerTest {

    private WarnEventService warnEventService;
    private DisposeTaskService disposeTaskService;
    private RuleService ruleService;
    private NotifyService notifyService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        warnEventService = mock(WarnEventService.class);
        disposeTaskService = mock(DisposeTaskService.class);
        ruleService = mock(RuleService.class);
        notifyService = mock(NotifyService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new WarnController(warnEventService, disposeTaskService, ruleService, notifyService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        LoginContext.setUser(new LoginContext.LoginUser(9L, "disp01",
                Set.of(), Set.of("DISPATCHER"), Set.of(4L), 2));
        LoginContext.setScope(DataScope.all());
    }

    @AfterEach
    void tearDown() {
        LoginContext.clear();
    }

    @Test
    void eventsPageDelegates() throws Exception {
        when(warnEventService.page(eq(4L), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt(), any(DataScope.class)))
                .thenReturn(new PageResult<>(1L, List.of(new WarnEventEntity())));
        mockMvc.perform(get("/api/v1/warn/events").param("tunnelId", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        verify(warnEventService).page(eq(4L), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt(), any(DataScope.class));
    }

    @Test
    void confirmDelegatesWithLoginUser() throws Exception {
        mockMvc.perform(post("/api/v1/warn/events/5/confirm")
                        .contentType("application/json")
                        .content("{\"result\":1,\"conclusion\":\"true-hit\"}"))
                .andExpect(status().isOk());
        verify(warnEventService).confirm(eq(5L), eq(9L), eq("disp01"), eq(true), eq("true-hit"));
    }

    @Test
    void confirmFalseAlarmPassesFalse() throws Exception {
        mockMvc.perform(post("/api/v1/warn/events/5/confirm")
                        .contentType("application/json")
                        .content("{\"result\":2,\"conclusion\":\"noise\"}"))
                .andExpect(status().isOk());
        verify(warnEventService).confirm(eq(5L), eq(9L), eq("disp01"), eq(false), eq("noise"));
    }

    @Test
    void dispatchCreatesTask() throws Exception {
        when(disposeTaskService.dispatch(anyLong(), anyLong(), anyLong(), anyString(),
                anyString(), any())).thenReturn(300L);
        mockMvc.perform(post("/api/v1/warn/tasks")
                        .contentType("application/json")
                        .content("{\"eventId\":5,\"assigneeId\":8,\"measure\":\"x\","
                                + "\"deadline\":\"2026-09-30T10:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(300));
    }

    @Test
    void tasksPageReturnsPageResult() throws Exception {
        when(disposeTaskService.page(any(), any(), anyInt(), anyInt(), any(DataScope.class)))
                .thenReturn(new PageResult<>(0L, List.of(new DisposeTaskEntity())));
        mockMvc.perform(get("/api/v1/warn/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void createRuleValidatesViaService() throws Exception {
        when(ruleService.create(any(RuleService.RuleCmd.class)))
                .thenThrow(new com.tgaws.common.exception.BizException(
                        com.tgaws.common.result.ErrorCode.A0002, "rule name required"));
        mockMvc.perform(post("/api/v1/warn/rules")
                        .contentType("application/json")
                        .content("{\"ruleType\":1,\"warnLevel\":3,\"expressionJson\":\"{}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("A0002"));
    }

    @Test
    void notifyLogsRequiresEventScope() throws Exception {
        WarnEventEntity event = new WarnEventEntity();
        event.setTunnelId(4L);
        when(warnEventService.getById(5L)).thenReturn(event);
        when(notifyService.listLogs(eq(5L), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(0L, List.of()));
        mockMvc.perform(get("/api/v1/warn/notify-logs").param("eventId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void statsParsesRange() throws Exception {
        when(warnEventService.stats(any(), any(), any(DataScope.class))).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/warn/events/stats")
                        .param("from", "2026-09-01T00:00:00")
                        .param("to", "2026-09-29T00:00:00"))
                .andExpect(status().isOk());
    }
}
