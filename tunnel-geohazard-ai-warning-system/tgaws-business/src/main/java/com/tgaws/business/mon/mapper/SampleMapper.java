package com.tgaws.business.mon.mapper;

import com.tgaws.business.mon.entity.SampleEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 原始采样 Mapper（data_sample：主键 (point_id,ts) 天然去重，批量幂等覆盖）。
 */
public interface SampleMapper {

    /**
     * 批量插入（ON DUPLICATE KEY UPDATE 幂等覆盖补传重放）。
     *
     * @param rows 采样实体列表（ts 为 LocalDateTime，毫秒精度）
     * @return 影响行数
     */
    int batchInsert(@Param("rows") List<SampleEntity> rows);
}
