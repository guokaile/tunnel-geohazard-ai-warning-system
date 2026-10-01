package com.tgaws.business.sys.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 操作审计 Mapper（sys_oper_log，仅追加：无 update/delete 语句；
 * 防篡改哈希链：audit_hash = SHA-256(上一行 audit_hash + 本行规范化内容)）。
 */
public interface OperLogMapper {

    /** 链尾哈希（无历史行返回 null） */
    String selectLastHash();

    /** 追加审计行（含链哈希） */
    int insert(@Param("userId") Long userId, @Param("username") String username,
               @Param("module") String module, @Param("operation") String operation,
               @Param("method") String method, @Param("requestParams") String requestParams,
               @Param("responseCode") String responseCode, @Param("costMs") Integer costMs,
               @Param("ip") String ip, @Param("operTime") LocalDateTime operTime,
               @Param("auditHash") String auditHash);
}
