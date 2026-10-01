package com.tgaws.web.controller;

import com.tgaws.business.mon.entity.PointEntity;
import com.tgaws.business.mon.manager.MonQueryService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 监控域查询控制器单测（T-811，standalone MockMvc：数据出口 C0008/分页钳制/正常链路）。
 */
class MonControllerTest {

    private MonQueryService monQueryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        monQueryService = mock(MonQueryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MonController(monQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        LoginContext.setUser(new LoginContext.LoginUser(9L, "disp01",
                Set.of(), Set.of("DISPATCHER"), Set.of(4L), 2));
    }

    @AfterEach
    void tearDown() {
        LoginContext.clear();
    }

    @Test
    void tunnelsOk() throws Exception {
        when(monQueryService.listTunnels(any())).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/mon/tunnels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"));
    }

    @Test
    void pointDetailOutOfScopeReturnsC0008() throws Exception {
        PointEntity point = new PointEntity();
        point.setId(7L);
        point.setTunnelId(5L);
        when(monQueryService.getPoint(anyLong())).thenReturn(point);
        LoginContext.setScope(DataScope.ofTunnels(Set.of(4L)));
        mockMvc.perform(get("/api/v1/mon/points/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("C0008"));
    }

    @Test
    void pointDetailInScopeOk() throws Exception {
        PointEntity point = new PointEntity();
        point.setId(7L);
        point.setTunnelId(4L);
        when(monQueryService.getPoint(anyLong())).thenReturn(point);
        LoginContext.setScope(DataScope.ofTunnels(Set.of(4L)));
        mockMvc.perform(get("/api/v1/mon/points/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"));
    }

    @Test
    void seriesClampsPageSizeAndParsesDates() throws Exception {
        PointEntity point = new PointEntity();
        point.setId(7L);
        point.setTunnelId(4L);
        when(monQueryService.getPoint(anyLong())).thenReturn(point);
        when(monQueryService.series(anyLong(), any(), any(), any())).thenReturn(List.of());
        LoginContext.setScope(DataScope.all());
        mockMvc.perform(get("/api/v1/mon/points/7/series")
                        .param("from", "2026-09-29T00:00:00")
                        .param("to", "2026-09-29T12:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"));
    }

    @Test
    void pointsClampsPageSize() throws Exception {
        when(monQueryService.pagePoints(any(), any(), any(), any(), any(), anyInt(), eq(200), any()))
                .thenReturn(new PageResult<>(0L, List.of()));
        LoginContext.setScope(DataScope.all());
        mockMvc.perform(get("/api/v1/mon/points").param("pageSize", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"));
    }

    @Test
    void overviewOk() throws Exception {
        when(monQueryService.overview(any())).thenReturn(new com.tgaws.business.mon.vo.MonQueryVos.OverviewVo());
        mockMvc.perform(get("/api/v1/mon/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"));
    }
}
