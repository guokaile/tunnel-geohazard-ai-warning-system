package com.tgaws.common.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 雪花 ID 生成器单测（口径：仅文件命名与业务单号，不用于采样表主键）。
 */
class SnowflakeIdGeneratorTest {

    @Test
    void generateUniqueAndPositive() {
        SnowflakeIdGenerator gen = SnowflakeIdGenerator.defaultInstance();
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 100_000; i++) {
            long id = gen.nextId();
            assertTrue(id > 0, "id 必须为正数");
            assertTrue(ids.add(id), "id 重复: " + id);
        }
    }

    @Test
    void rejectIllegalWorkerId() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(32L, 0L));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(0L, -1L));
    }
}
