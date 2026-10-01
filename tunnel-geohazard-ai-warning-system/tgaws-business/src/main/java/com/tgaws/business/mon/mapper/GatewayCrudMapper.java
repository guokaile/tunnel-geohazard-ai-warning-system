package com.tgaws.business.mon.mapper;

import com.tgaws.business.mon.entity.GatewayEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 网关台账 Mapper（mon_gateway CRUD；列表不查 secret 密文，只返回配置标志）。
 */
public interface GatewayCrudMapper {

    int insert(GatewayEntity entity);

    /** 更新：secret 为 null 时不动该列（"不传=不变更"语义，评审 4.4） */
    int update(GatewayEntity entity);

    /** 列表：不解密、不返回密钥，只给 secretConfigured 布尔标志（评审 4.3） */
    List<GatewayEntity> selectList(@Param("keyword") String keyword);

    /** 详情（含密文，仅内部使用；对外 VO 不含 secret 字段） */
    GatewayEntity selectById(@Param("id") long id);

    int updateStatus(@Param("id") long id, @Param("status") int status);

    int logicDelete(@Param("id") long id);
}
