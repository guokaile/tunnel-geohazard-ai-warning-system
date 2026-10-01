package com.tgaws.business.mon.manager;

import com.tgaws.business.SampleStoreTestConfig;
import com.tgaws.business.mon.mapper.PointMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * W4-4c 真实库集成测试（tgaws_test）：
 * ① ON DUPLICATE 幂等覆盖（补传重放不产生重复行）② 毫秒去重 ③ Latest upsert ④ PointMapper 解析。
 *
 * <p>@Transactional 自动回滚，测试数据不污染测试库基线。</p>
 */
@SpringBootTest(classes = SampleStoreTestConfig.class)
@ActiveProfiles("test")
@Transactional
class SampleStoreIntegrationTest {

    private static final ZoneId DB_ZONE = ZoneId.of("Asia/Shanghai");
    private static final long TS_BASE = 1790301600000L; // 2026-09-25 10:00:00 CST

    @Autowired
    private SampleStoreManager storeManager;

    @Autowired
    private PointMapper pointMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM data_sample");
        jdbcTemplate.update("DELETE FROM data_point_latest");
        jdbcTemplate.update("DELETE FROM mon_point WHERE point_code LIKE 'IT-%'");
        jdbcTemplate.update("DELETE FROM prj_tunnel WHERE tunnel_code = 'IT-T1'");
    }

    @Test
    void onDuplicateKeyUpdatesIdempotently() {
        // 同点位同毫秒两次写入（模拟补传重放）→ 幂等覆盖，仅 1 行
        int n1 = storeManager.batchInsert(List.of(row(1L, TS_BASE, "10.0000")));
        int n2 = storeManager.batchInsert(List.of(row(1L, TS_BASE, "99.9999")));
        assertEquals(1, n1, "首次插入影响 1 行");
        // MySQL 语义：INSERT..ON DUPLICATE KEY UPDATE 返回 1=插入、2=更新、0=无变化
        assertEquals(2, n2, "重复键走更新路径影响 2 行（幂等覆盖语义）");
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM data_sample WHERE point_id=1 AND ts=?", Integer.class,
                LocalDateTime.ofInstant(Instant.ofEpochMilli(TS_BASE), DB_ZONE));
        assertEquals(1, cnt, "补传重放不得产生重复行");
        BigDecimal v = jdbcTemplate.queryForObject(
                "SELECT value FROM data_sample WHERE point_id=1 AND ts=? LIMIT 1", BigDecimal.class,
                LocalDateTime.ofInstant(Instant.ofEpochMilli(TS_BASE), DB_ZONE));
        assertEquals(0, new BigDecimal("99.9999").compareTo(v), "后写覆盖前值");
    }

    @Test
    void millisecondPrecisionKeepsDistinctRows() {
        storeManager.batchInsert(List.of(row(2L, TS_BASE, "1.0")));
        storeManager.batchInsert(List.of(row(2L, TS_BASE + 1, "2.0")));
        storeManager.batchInsert(List.of(row(2L, TS_BASE + 2, "3.0")));
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM data_sample WHERE point_id=2", Integer.class);
        assertEquals(3, cnt, "同点 1ms 间隔应独立成行（datetime(3)）");
    }

    @Test
    void latestUpsertOverwrites() {
        storeManager.upsert(row(3L, TS_BASE, "5.0"));
        storeManager.upsert(row(3L, TS_BASE + 1000, "6.0"));
        BigDecimal v = jdbcTemplate.queryForObject(
                "SELECT value FROM data_point_latest WHERE point_id=3", BigDecimal.class);
        assertEquals(0, new BigDecimal("6.0").compareTo(v));
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM data_point_latest WHERE point_id=3", Integer.class);
        assertEquals(1, cnt, "最新值单行/点位");
    }

    @Test
    void pointResolverMapsConfiguredCode() {
        jdbcTemplate.update("INSERT INTO prj_tunnel (tunnel_code, tunnel_name) VALUES ('IT-T1','IT隧道')");
        jdbcTemplate.update(
                "INSERT INTO mon_point (point_code, point_name, tunnel_id, hazard_type, item_type) "
                        + "SELECT 'IT-P001', 'IT点位', id, 3, 301 FROM prj_tunnel WHERE tunnel_code='IT-T1'");
        Long pointId = storeManager.resolvePointId("IT-P001");
        assertNotNull(pointId, "启用点位应解析成功");
        assertEquals(pointId, pointMapper.selectIdByCode("IT-P001"));
        assertNull(storeManager.resolvePointId("IT-NOT-EXIST"), "未配置点位应返回 null");
    }

    private static com.tgaws.common.store.SampleRow row(long pointId, long tsMs, String value) {
        return new com.tgaws.common.store.SampleRow(pointId, tsMs, new BigDecimal(value), 0, 1);
    }
}
