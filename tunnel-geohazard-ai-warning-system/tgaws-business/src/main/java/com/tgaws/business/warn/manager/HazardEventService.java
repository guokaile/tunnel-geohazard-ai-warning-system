package com.tgaws.business.warn.manager;

import com.tgaws.business.warn.entity.HazardEventEntity;
import com.tgaws.business.warn.mapper.HazardEventMapper;
import com.tgaws.common.exception.BizException;
import com.tgaws.common.result.ErrorCode;
import com.tgaws.common.util.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 灾害险情登记服务（FR-407 + 评审 3.2/3.5）：
 *
 * <ul>
 *   <li><b>event_time 三重校验</b>（模型评估真值锚点，NFR-A1/A3 依赖）：
 *       ①不得晚于当前时间；②不得早于当前时间 30 天（防提前量虚高到无意义）；
 *       ③不得晚于登记时刻（create_time）；</li>
 *   <li><b>关联一对多（评审 3.1 方案A）</b>：relate_event_id 存主关联，
 *       全部关联经 warn_event.hazard_event_id 反向（T-603 linkHazard 已就位，
 *       各关联事件时间线各插节点11）；</li>
 *   <li><b>巡检隐患边界（评审 3.5）</b>：巡检隐患达灾害级时经本登记转灾害
 *       （patrolHazardId 留痕），FR-407 真值样本不遗漏。</li>
 * </ul>
 */
@Service
public class HazardEventService {

    private static final Logger log = LoggerFactory.getLogger(HazardEventService.class);

    private static final ZoneId DB_ZONE = ZoneId.of("Asia/Shanghai");

    /** event_time 最早回溯（天）：防误填导致提前量虚高 */
    private static final int MAX_BACKTRACK_DAYS = 30;

    private final HazardEventMapper hazardEventMapper;
    private final WarnEventService warnEventService;
    private final SnowflakeIdGenerator idGenerator = SnowflakeIdGenerator.defaultInstance();

    public HazardEventService(HazardEventMapper hazardEventMapper, WarnEventService warnEventService) {
        this.hazardEventMapper = hazardEventMapper;
        this.warnEventService = warnEventService;
    }

    /**
     * 登记灾害险情。
     *
     * @param relateEventIds 关联预警事件 id 列表（一对多，可空）
     */
    @Transactional
    public long register(RegisterCmd cmd, Long userId, String userName, List<Long> relateEventIds) {
        if (cmd.tunnelId() == null) {
            throw new BizException(ErrorCode.A0002, "隧道必填");
        }
        if (cmd.hazardType() == null || cmd.hazardType() < 1 || cmd.hazardType() > 6) {
            throw new BizException(ErrorCode.A0002, "灾害类型必须为 1坍塌/2涌水突水/3瓦斯/4突泥/5沉降/6收敛位移");
        }
        if (cmd.level() != null && (cmd.level() < 1 || cmd.level() > 4)) {
            throw new BizException(ErrorCode.A0002, "灾害级别必须为 1蓝/2黄/3橙/4红");
        }
        validateEventTime(cmd.eventTime());
        HazardEventEntity entity = new HazardEventEntity();
        entity.setHazardNo(LocalDate.now(DB_ZONE).format(DateTimeFormatter.ofPattern("yyMMdd"))
                + String.format("%06d", idGenerator.nextId() % 1_000_000L));
        entity.setTunnelId(cmd.tunnelId());
        entity.setSectionId(cmd.sectionId());
        entity.setHazardType(cmd.hazardType());
        entity.setEventTime(cmd.eventTime());
        entity.setPosition(cmd.position());
        entity.setConsequence(cmd.consequence());
        entity.setLevel(cmd.level() == null ? 1 : cmd.level());
        entity.setRelateEventId(relateEventIds == null || relateEventIds.isEmpty()
                ? null : relateEventIds.get(0));
        entity.setPatrolHazardId(cmd.patrolHazardId());
        entity.setRegisterUserId(userId);
        entity.setRemark(cmd.remark());
        hazardEventMapper.insert(entity);
        // 全部关联事件反向关联 + 时间线"灾变确认"节点（评审 3.1/4.3）
        if (relateEventIds != null && !relateEventIds.isEmpty()) {
            warnEventService.linkHazard(entity.getId(), relateEventIds, userId, userName);
        }
        log.info("灾害险情登记：{} event_time={} 关联预警 {} 条 来源隐患 {}",
                entity.getHazardNo(), cmd.eventTime(), relateEventIds == null ? 0 : relateEventIds.size(),
                cmd.patrolHazardId());
        return entity.getId();
    }

    /** 灾害险情列表（API-C22：tunnelId 必填 + hazardType/时间范围过滤） */
    public List<HazardEventEntity> list(Long tunnelId, Integer hazardType,
                                        LocalDateTime from, LocalDateTime to) {
        if (tunnelId == null) {
            throw new BizException(ErrorCode.A0002, "隧道必填");
        }
        return hazardEventMapper.selectByTunnel(tunnelId, hazardType, from, to);
    }

    /** event_time 三重校验（评审 3.2） */
    static void validateEventTime(LocalDateTime eventTime) {
        if (eventTime == null) {
            throw new BizException(ErrorCode.A0002, "灾害发生时间必填（模型评估真值锚点）");
        }
        LocalDateTime now = LocalDateTime.now(DB_ZONE);
        if (eventTime.isAfter(now)) {
            throw new BizException(ErrorCode.A0002, "灾害发生时间不得晚于当前时间");
        }
        if (eventTime.isBefore(now.minusDays(MAX_BACKTRACK_DAYS))) {
            throw new BizException(ErrorCode.A0002, "灾害发生时间不得早于当前时间 " + MAX_BACKTRACK_DAYS + " 天");
        }
    }

    /** 登记命令 */
    public record RegisterCmd(Long tunnelId, Long sectionId, Integer hazardType,
                              LocalDateTime eventTime, String position, String consequence,
                              Integer level, Long patrolHazardId, String remark) {
    }
}
