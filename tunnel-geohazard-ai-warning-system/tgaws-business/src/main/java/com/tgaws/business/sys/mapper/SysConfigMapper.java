package com.tgaws.business.sys.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 系统配置 Mapper（sys_config，T-703 限流阈值等运行参数）。
 */
public interface SysConfigMapper {

    String selectByKey(@Param("configKey") String configKey);
}
