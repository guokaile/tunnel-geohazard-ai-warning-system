package com.tgaws.common.store;

import java.util.List;

/**
 * 采样存储契约（位于 common：access/compute/business 三方均可引用，
 * 实现放 business，装配在 web——沿用 IDataPipeline 已验证模式，不引入反向依赖）。
 */
public interface ISampleStore {

    /**
     * 批量插入原始采样（幂等覆盖：主键 (point_id, ts) + ON DUPLICATE KEY UPDATE，
     * 补传重放不产生重复行）。
     *
     * @param rows 采样行列表
     * @return 成功写入行数
     */
    int batchInsert(List<SampleRow> rows);
}
