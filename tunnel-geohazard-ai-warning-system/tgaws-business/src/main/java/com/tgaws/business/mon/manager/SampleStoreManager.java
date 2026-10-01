package com.tgaws.business.mon.manager;

import com.tgaws.business.mon.entity.SampleEntity;
import com.tgaws.business.mon.mapper.LatestMapper;
import com.tgaws.business.mon.mapper.PointMapper;
import com.tgaws.business.mon.mapper.SampleMapper;
import com.tgaws.common.store.IPointLatestStore;
import com.tgaws.common.store.IPointResolver;
import com.tgaws.common.store.ISampleStore;
import com.tgaws.common.store.SampleRow;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 采样存储适配（common 契约 → MyBatis 实现；装配在 web 层注入）。
 *
 * <p>实现 common.store 三契约：ISampleStore（批量写）/ IPointLatestStore（最新值）/
 * IPointResolver（点位编码解析），使 compute 管道经 common 依赖即可读写采样。</p>
 */
@Component
public class SampleStoreManager implements ISampleStore, IPointLatestStore, IPointResolver {

    /** 数据库会话时区（与 JDBC serverTimezone=Asia/Shanghai 一致） */
    private static final ZoneId DB_ZONE = ZoneId.of("Asia/Shanghai");

    private final SampleMapper sampleMapper;
    private final LatestMapper latestMapper;
    private final PointMapper pointMapper;

    public SampleStoreManager(SampleMapper sampleMapper, LatestMapper latestMapper, PointMapper pointMapper) {
        this.sampleMapper = sampleMapper;
        this.latestMapper = latestMapper;
        this.pointMapper = pointMapper;
    }

    @Override
    public Long resolvePointId(String pointCode) {
        return pointMapper.selectIdByCode(pointCode);
    }

    @Override
    public int batchInsert(List<SampleRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        List<SampleEntity> entities = new ArrayList<>(rows.size());
        for (SampleRow row : rows) {
            entities.add(toEntity(row));
        }
        return sampleMapper.batchInsert(entities);
    }

    @Override
    public void upsert(SampleRow row) {
        latestMapper.upsert(toEntity(row));
    }

    private static SampleEntity toEntity(SampleRow row) {
        SampleEntity entity = new SampleEntity();
        entity.setPointId(row.pointId());
        entity.setTs(toLocalDateTime(row.tsMs()));
        entity.setValue(row.value());
        entity.setQuality(row.quality());
        entity.setSource(row.source());
        entity.setReceiveTime(LocalDateTime.now(DB_ZONE));
        return entity;
    }

    private static LocalDateTime toLocalDateTime(long epochMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), DB_ZONE);
    }
}
