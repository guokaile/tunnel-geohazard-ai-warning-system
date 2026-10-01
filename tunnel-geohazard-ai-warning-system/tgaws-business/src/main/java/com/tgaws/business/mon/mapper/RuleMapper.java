package com.tgaws.business.mon.mapper;

import com.tgaws.business.mon.entity.RuleEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 预警规则 Mapper（mon_rule：判定编排的启用规则查询 + T-708 规则管理 CRUD）。
 */
public interface RuleMapper {

    /** 查某测项匹配的启用规则（item_type=0 全测项 或 =指定测项） */
    List<RuleEntity> selectEnabledByItemType(@Param("itemType") int itemType);

    // ---------- T-708 规则管理（版本化：uk_rule_version(rule_code, version)） ----------

    int insert(RuleEntity entity);

    RuleEntity selectById(@Param("id") long id);

    /** 最新版本每规则一条（历史版本留痕） */
    List<RuleEntity> selectLatestList(@Param("hazardType") Integer hazardType,
                                      @Param("itemType") Integer itemType,
                                      @Param("status") Integer status);

    /** 同规则码全版本历史（按版本倒序） */
    List<RuleEntity> selectHistory(@Param("ruleCode") String ruleCode);

    /** 被预警事件引用计数（删除守卫 B0204） */
    int countEventRefs(@Param("ruleId") long ruleId);

    /** 软删该规则码全部版本 */
    int deleteByCode(@Param("ruleCode") String ruleCode);

    int updateStatus(@Param("id") long id, @Param("status") int status);
}
