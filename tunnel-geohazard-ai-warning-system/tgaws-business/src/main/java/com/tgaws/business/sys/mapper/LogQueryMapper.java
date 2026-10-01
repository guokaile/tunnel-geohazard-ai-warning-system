package com.tgaws.business.sys.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 审计日志查询 Mapper（T-813：A22 操作日志/A23 登录日志，只读；防篡改哈希链列不返回）。
 */
public interface LogQueryMapper {

    List<Map<String, Object>> selectOperPage(@Param("username") String username,
                                             @Param("module") String module,
                                             @Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to,
                                             @Param("offset") int offset,
                                             @Param("limit") int limit);

    long countOperPage(@Param("username") String username, @Param("module") String module,
                       @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    List<Map<String, Object>> selectLoginPage(@Param("username") String username,
                                              @Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit);

    long countLoginPage(@Param("username") String username,
                        @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
