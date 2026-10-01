package com.tgaws.business.sys.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 第三方应用注册表 Mapper（sys_third_app，T-702 Open API 签名）。
 */
public interface ThirdAppMapper {

    /** 按应用标识查注册信息（密文原样返回，服务层解密） */
    ThirdAppRow selectByAppKey(@Param("appKey") String appKey);

    /** 注册行（不含逻辑删除字段，审计表口径） */
    record ThirdAppRow(String appKey, String appSecret, String secretOld,
                       LocalDateTime secretRotateTime, String appName,
                       Integer enabled, LocalDateTime expireTime, String ipWhitelist) {
    }
}
