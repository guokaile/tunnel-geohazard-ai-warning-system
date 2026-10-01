package com.tgaws.common.store;

/**
 * 点位最新值存储契约（data_point_latest upsert 维护，看板轮询数据源）。
 */
public interface IPointLatestStore {

    /** 最新值 upsert（单行/点位） */
    void upsert(SampleRow row);
}
