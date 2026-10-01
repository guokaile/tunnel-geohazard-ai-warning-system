package com.tgaws.common.store;

/**
 * 点位编码解析契约（point_code → point_id；实现查 mon_point，未配置返回 null）。
 */
public interface IPointResolver {

    /** 解析点位 id；未配置返回 null（调用方按 B0101 语义处理） */
    Long resolvePointId(String pointCode);
}
