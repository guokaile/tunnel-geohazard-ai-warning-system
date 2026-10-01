package com.tgaws.business.data.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 分区维护 Mapper（T-709，NFR-DATA1/《3》3.5.1 落地）：
 * REORGANIZE pmax 拆出新月分区 / DROP 过期分区 / pmax 兜底巡检 / 留痕。
 * 表名仅由服务层白名单常量传入（data_sample/data_sample_minute），
 * 分区名 pYYYYMM 由服务层按日期生成——杜绝拼接注入。
 */
public interface PartitionMaintenanceMapper {

    /** REORGANIZE pmax → (新月分区, pmax)；幂等由服务层先查存在性 */
    int reorganizePmax(@Param("table") String table, @Param("partition") String partition,
                       @Param("boundary") String boundary);

    /** 删除 boundary 之前的非 pmax 分区（动态 DROP，逐一执行） */
    int dropPartition(@Param("table") String table, @Param("partition") String partition);

    /** 指定分区行数（DROP 前留痕 / pmax 有数据告警） */
    long partitionRowCount(@Param("table") String table, @Param("partition") String partition);

    /** 分区现状：name/boundary/rows（每日巡检） */
    List<Map<String, Object>> partitionsOf(@Param("table") String table);

    /** 归档留痕（仅追加） */
    int insertArchiveLog(@Param("tableName") String tableName,
                         @Param("partitionName") String partitionName,
                         @Param("action") int action,
                         @Param("boundaryTime") LocalDateTime boundaryTime,
                         @Param("rowCount") Long rowCount,
                         @Param("result") int result,
                         @Param("remark") String remark);
}
