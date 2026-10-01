package com.tgaws.web.controller;

import com.tgaws.business.sys.health.SystemHealthService;
import com.tgaws.web.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 系统健康检查控制器单测（W8 API-S01，standalone MockMvc）。
 */
class SystemControllerTest {

    private SystemHealthService healthService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        healthService = mock(SystemHealthService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SystemController(healthService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void healthDbUp() throws Exception {
        when(healthService.checkDb()).thenReturn(true);
        mockMvc.perform(get("/api/v1/system/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.db").value("UP"))
                .andExpect(jsonPath("$.data.app").value("tgaws"));
    }

    @Test
    void healthDbDown() throws Exception {
        when(healthService.checkDb()).thenReturn(false);
        mockMvc.perform(get("/api/v1/system/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DEGRADED"))
                .andExpect(jsonPath("$.data.db").value("DOWN"));
    }
}
