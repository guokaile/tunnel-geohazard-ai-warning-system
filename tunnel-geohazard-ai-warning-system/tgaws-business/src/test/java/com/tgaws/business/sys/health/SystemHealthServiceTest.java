package com.tgaws.business.sys.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 系统健康检查服务单测（W8）：DB 连通 / 失败 / 异常三场景。
 */
class SystemHealthServiceTest {

    private DataSource dataSource;
    private Connection connection;
    private SystemHealthService service;

    @BeforeEach
    void setUp() {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        service = new SystemHealthService(dataSource);
    }

    @Test
    void checkDbUp() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(true);
        assertTrue(service.checkDb());
        verify(connection).close();
    }

    @Test
    void checkDbInvalid() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(false);
        assertFalse(service.checkDb());
    }

    @Test
    void checkDbConnectionFailed() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("refused"));
        assertFalse(service.checkDb());
    }
}
