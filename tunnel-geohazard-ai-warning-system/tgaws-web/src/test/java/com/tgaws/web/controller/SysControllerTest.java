package com.tgaws.web.controller;

import com.tgaws.business.sys.manager.LogQueryService;
import com.tgaws.business.sys.manager.RoleAdminService;
import com.tgaws.business.sys.manager.UserAdminService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 系统管理控制器单测（T-813，standalone MockMvc：分页钳制/创建/改密入口）。
 */
class SysControllerTest {

    private UserAdminService userAdminService;
    private RoleAdminService roleAdminService;
    private LogQueryService logQueryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userAdminService = mock(UserAdminService.class);
        roleAdminService = mock(RoleAdminService.class);
        logQueryService = mock(LogQueryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new SysController(userAdminService, roleAdminService, logQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        LoginContext.setUser(new LoginContext.LoginUser(1L, "admin",
                Set.of(), Set.of("ADMIN"), Set.of(1L), 1));
    }

    @AfterEach
    void tearDown() {
        LoginContext.clear();
    }

    @Test
    void usersPageClampsSize() throws Exception {
        when(userAdminService.page(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new com.tgaws.common.result.PageResult<>(0L, List.of()));
        mockMvc.perform(get("/api/v1/sys/users").param("pageSize", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"));
    }

    @Test
    void createUserOk() throws Exception {
        when(userAdminService.create(any(), any(), any(), any())).thenReturn(5L);
        mockMvc.perform(post("/api/v1/sys/users")
                        .contentType("application/json")
                        .content("{\"username\":\"u1\",\"realName\":\"张三\",\"roleIds\":[2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(5));
    }

    @Test
    void rolesOk() throws Exception {
        when(roleAdminService.list(any())).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/sys/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"));
    }

    @Test
    void permissionsTreeOk() throws Exception {
        when(roleAdminService.permissionTree()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/sys/permissions"))
                .andExpect(status().isOk());
    }

    @Test
    void assignPermissionsOk() throws Exception {
        mockMvc.perform(put("/api/v1/sys/roles/2/permissions")
                        .contentType("application/json")
                        .content("{\"permIds\":[11,13]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"));
    }

    @Test
    void operLogsOk() throws Exception {
        when(logQueryService.operPage(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new com.tgaws.common.result.PageResult<>(0L, List.of()));
        mockMvc.perform(get("/api/v1/sys/logs/oper"))
                .andExpect(status().isOk());
    }
}
