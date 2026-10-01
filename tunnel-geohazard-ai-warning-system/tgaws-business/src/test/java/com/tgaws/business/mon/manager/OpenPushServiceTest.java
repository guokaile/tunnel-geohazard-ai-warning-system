package com.tgaws.business.mon.manager;

import com.tgaws.common.store.SampleRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 第三方推送接收单测（T-702/FR-105：点位/时间/数值/质量位校验，source=5 入库）。
 */
class OpenPushServiceTest {

    private SampleStoreManager sampleStoreManager;
    private OpenPushService service;

    @BeforeEach
    void setUp() {
        sampleStoreManager = mock(SampleStoreManager.class);
        service = new OpenPushService(sampleStoreManager);
    }

    private Map<String, Object> item(String code, String ts, String value, int quality) {
        return Map.of("pointCode", code, "ts", ts, "value", value, "quality", quality);
    }

    @Test
    void validItemsInsertedAsThirdPartySource() {
        when(sampleStoreManager.resolvePointId("P00010001")).thenReturn(10L);
        when(sampleStoreManager.resolvePointId("P00010002")).thenReturn(11L);
        when(sampleStoreManager.batchInsert(anyList())).thenReturn(2);
        OpenPushService.PushResult result = service.push("B1", List.of(
                item("P00010001", "2026-09-28 10:00:00", "12.3456", 0),
                item("P00010002", "2026-09-28 10:00:30", "0.52", 2)));
        assertEquals(2, result.received());
        assertEquals(0, result.failed());
        ArgumentCaptor<List<SampleRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(sampleStoreManager).batchInsert(captor.capture());
        SampleRow row = captor.getValue().get(0);
        assertEquals(10L, row.pointId());
        assertEquals(new BigDecimal("12.3456"), row.value());
        assertEquals(5, row.source(), "source=5 第三方（字典 data_source）");
    }

    @Test
    void invalidItemsCountedFailed() {
        when(sampleStoreManager.resolvePointId("P00010001")).thenReturn(10L);
        when(sampleStoreManager.resolvePointId("P00010002")).thenReturn(null);
        when(sampleStoreManager.batchInsert(anyList())).thenReturn(1);
        OpenPushService.PushResult result = service.push("B1", List.of(
                item("P00010001", "2026-09-28 10:00:00", "1.0", 0),
                item("P00010002", "2026-09-28 10:00:00", "1.0", 0),  // 点位不存在
                item("P00010001", "bad-time", "1.0", 0),             // 时间格式错误
                item("P00010001", "2026-09-28 10:00:00", "abc", 0),  // 数值错误
                item("P00010001", "2026-09-28 10:00:00", "1.0", 9))); // 质量位越界
        assertEquals(1, result.received());
        assertEquals(4, result.failed());
    }

    @Test
    void emptyBatchNoRows() {
        when(sampleStoreManager.batchInsert(anyList())).thenReturn(0);
        OpenPushService.PushResult result = service.push("B0", List.of());
        assertEquals(0, result.received());
        assertEquals(0, result.failed());
    }
}
