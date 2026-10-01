package com.tgaws.business.sys.log;

import com.tgaws.business.sys.mapper.OperLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审计记录器单测（T-703：哈希链/脱敏/异步降级不阻断业务）。
 */
class OperLogRecorderTest {

    private OperLogMapper operLogMapper;
    private OperLogRecorder recorder;

    @BeforeEach
    void setUp() {
        operLogMapper = mock(OperLogMapper.class);
        recorder = new OperLogRecorder(operLogMapper);
    }

    private OperLogRecorder.Entry entry() {
        return new OperLogRecorder.Entry(7L, "disp01", "巡检管理", "创建巡检计划",
                "PatrolController.createPlan", "{\"planName\":\"p1\"}", "00000", 12, "127.0.0.1",
                LocalDateTime.of(2026, 9, 29, 9, 0, 0));
    }

    @Test
    void firstRowChainsFromEmptyPrevHash() throws Exception {
        when(operLogMapper.selectLastHash()).thenReturn(null);
        recorder.record(entry());
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(operLogMapper, timeout(2000)).insert(any(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), any(), anyString(), any(), hashCaptor.capture());
        String hash = hashCaptor.getValue();
        assertEquals(64, hash.length(), "SHA-256 hex 64 位");
        assertEquals(OperLogRecorder.sha256("" + OperLogRecorder.canonical(entry())), hash);
    }

    @Test
    void subsequentRowChainsPrevHash() throws Exception {
        when(operLogMapper.selectLastHash()).thenReturn("ab".repeat(32));
        recorder.record(entry());
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(operLogMapper, timeout(2000)).insert(any(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), any(), anyString(), any(), hashCaptor.capture());
        assertEquals(OperLogRecorder.sha256("ab".repeat(32) + OperLogRecorder.canonical(entry())),
                hashCaptor.getValue(), "链哈希 = SHA-256(上一行哈希 + 本行内容)");
    }

    @Test
    void writeFailureDoesNotPropagate() throws Exception {
        // 异步写库失败：record() 不抛异常（降级仅 ERROR 日志，6.3.7 口径）
        when(operLogMapper.selectLastHash()).thenReturn(null);
        doThrow(new RuntimeException("db down")).when(operLogMapper).insert(any(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), any(),
                anyString(), any(), anyString());
        recorder.record(entry()); // 不应抛出
        Thread.sleep(300);        // 等异步线程执行（异常被吞并日志）
    }

    @Test
    void maskParamsMasksSensitivePairs() {
        String json = OperLogRecorder.maskParams(new Object[]{
                java.util.Map.of("password", "Tgaws@2026", "name", "张三")});
        assertFalse(json.contains("Tgaws@2026"), "口令值不得落审计");
        assertTrue(json.contains("password"), "字段名保留（值掩码）");
        assertTrue(json.contains("***"));
    }

    @Test
    void maskParamsKeepsNormalFieldsReadable() {
        String json = OperLogRecorder.maskParams(new Object[]{
                java.util.Map.of("planName", "T2隧道每日巡检")});
        assertTrue(json.contains("T2隧道每日巡检"));
        assertFalse(json.contains("***"));
    }

    @Test
    void maskParamsNullArgsReturnsNull() {
        assertNull(OperLogRecorder.maskParams(null));
        assertNull(OperLogRecorder.maskParams(new Object[0]));
    }

    @Test
    void sha256DeterministicAndDistinct() {
        assertEquals(OperLogRecorder.sha256("x"), OperLogRecorder.sha256("x"));
        assertNotEquals(OperLogRecorder.sha256("x"), OperLogRecorder.sha256("y"));
    }

    @Test
    void maskSensitiveJsonHandlesAllListedFields() {
        String json = "{\"token\":\"abc123\",\"secret\":\"s\",\"psk\":\"p\",\"pwd\":\"x\",\"credential\":\"c\"}";
        String masked = OperLogRecorder.maskSensitiveJson(json);
        assertFalse(masked.contains("abc123"));
        assertFalse(masked.contains("\"s\""));
        assertEquals(5, masked.split("\\*\\*\\*").length - 1, "5 个敏感对全部掩码");
    }

    @Test
    void insertReceivesMaskedParamsAndCodes() throws Exception {
        when(operLogMapper.selectLastHash()).thenReturn(null);
        // 脱敏在切面层完成（maskParams），记录器原样落库（单一职责）
        String maskedParams = OperLogRecorder.maskParams(new Object[]{
                java.util.Map.of("hazardType", 3, "password", "x")});
        assertFalse(maskedParams.contains("x"));
        OperLogRecorder.Entry e = new OperLogRecorder.Entry(7L, "disp01", "预警中心", "灾害登记",
                "HazardEventController.register", maskedParams, "A0004", 8, null,
                LocalDateTime.of(2026, 9, 29, 10, 0, 0));
        recorder.record(e);
        ArgumentCaptor<String> paramsCaptor = ArgumentCaptor.forClass(String.class);
        verify(operLogMapper, timeout(2000)).insert(any(), anyString(), anyString(), anyString(),
                anyString(), paramsCaptor.capture(), eq("A0004"), any(), any(), any(), anyString());
        assertEquals(maskedParams, paramsCaptor.getValue(), "切面脱敏后原样落库");
    }
}
