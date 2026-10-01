package com.tgaws.business.sys.auth;

import com.tgaws.business.sys.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理员口令注入单测（W8：幂等跳过/注入执行）。
 */
class AdminPasswordInitializerTest {

    @Test
    void skipWhenEnvEmpty() {
        UserMapper mapper = mock(UserMapper.class);
        new AdminPasswordInitializer(mapper, "").initialize();
        verify(mapper, org.mockito.Mockito.never()).updateAdminPasswordIfUntouched(anyString());
    }

    @Test
    void injectsBcryptHashWhenUntouched() {
        UserMapper mapper = mock(UserMapper.class);
        when(mapper.updateAdminPasswordIfUntouched(anyString())).thenReturn(1);
        new AdminPasswordInitializer(mapper, "Admin@2026Init").initialize();
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mapper).updateAdminPasswordIfUntouched(captor.capture());
        assertTrue(captor.getValue().startsWith("$2"), "应写入 BCrypt 哈希而非明文");
    }

    @Test
    void skipWhenAlreadyChanged() {
        UserMapper mapper = mock(UserMapper.class);
        when(mapper.updateAdminPasswordIfUntouched(anyString())).thenReturn(0);
        new AdminPasswordInitializer(mapper, "Admin@2026Init").initialize();
        verify(mapper).updateAdminPasswordIfUntouched(anyString());
    }
}
