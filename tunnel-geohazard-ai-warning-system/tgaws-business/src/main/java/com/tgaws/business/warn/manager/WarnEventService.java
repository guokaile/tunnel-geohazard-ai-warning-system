package com.tgaws.business.warn.manager;

import com.tgaws.business.warn.entity.TimelineEntity;
import com.tgaws.business.warn.entity.WarnEventEntity;
import com.tgaws.business.warn.mapper.TimelineMapper;
import com.tgaws.business.warn.mapper.WarnEventMapper;
import com.tgaws.common.datascope.DataScope;
import com.tgaws.common.datascope.DataScopeCondition;
import com.tgaws.common.datascope.DataScopeHelper;
import com.tgaws.common.exception.BizException;
import com.tgaws.common.result.PageResult;
import com.tgaws.common.result.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 预警事件状态机 + 时间线（T-603，FR-306/FR-406 落地点）：
 *
 * <ul>
 *   <li>全部流转用**条件 UPDATE**（乐观并发控制），影响行数 0 → B0302；</li>
 *   <li>每个流转在同一事务内追加时间线节点（仅追加不可篡改）；
 *      节点 detail 存**触发值快照**——trigger_value 被升级覆盖后历史仍可追溯（评审缺口闭环）；</li>
 *   <li>升级/降级各写节点（7/8），多次升降级全部留痕（upgrade_from_id 单链表达不了的）；</li>
 *   <li>linkHazard：灾害登记关联全部相关事件（节点 11 灾变确认，评审 4.3——
 *      一对多语义，从任一事件时间线均可看到灾变闭环）。</li>
 * </ul>
 */
@Service
public class WarnEventService {

    private static final Logger log = LoggerFactory.getLogger(WarnEventService.class);

    private static final ZoneId DB_ZONE = ZoneId.of("Asia/Shanghai");

    /** 节点类型 */
    public static final int NODE_GENERATED = 1;
    public static final int NODE_NOTIFIED = 2;
    public static final int NODE_CONFIRMED = 3;
    public static final int NODE_FALSE_ALARM = 4;
    public static final int NODE_DISPATCHED = 5;
    public static final int NODE_FEEDBACK = 6;
    public static final int NODE_UPGRADED = 7;
    public static final int NODE_DOWNGRADED = 8;
    public static final int NODE_PENDING_REVIEW = 9;
    public static final int NODE_CLOSED = 10;
    public static final int NODE_HAZARD_CONFIRMED = 11;

    private final WarnEventMapper warnEventMapper;
    private final TimelineMapper timelineMapper;

    /** 通知服务（Outbox；延迟注入防循环依赖：notify 依赖本服务做时间线节点） */
    private com.tgaws.business.warn.notify.NotifyService notifyService;

    public WarnEventService(WarnEventMapper warnEventMapper, TimelineMapper timelineMapper) {
        this.warnEventMapper = warnEventMapper;
        this.timelineMapper = timelineMapper;
    }

    /** 注入通知服务（@Lazy 打破与 NotifyService 的循环依赖：notify 需本服务写时间线节点） */
    @org.springframework.beans.factory.annotation.Autowired
    public void setNotifyService(
            @org.springframework.context.annotation.Lazy com.tgaws.business.warn.notify.NotifyService notifyService) {
        this.notifyService = notifyService;
    }

    /** B0306 去重判断（编排层调用） */
    public boolean hasActiveEvent(long pointId) {
        return warnEventMapper.selectActiveByPoint(pointId) != null;
    }

    /** 事件生成（插入 + 时间线节点1=生成，detail 含触发值快照 + Outbox 通知排队） */
    @Transactional
    public long createEvent(WarnEventEntity entity, String triggerSnapshot) {
        entity.setTriggerValue(triggerSnapshot);
        warnEventMapper.insert(entity);
        appendNode(entity.getId(), NODE_GENERATED, 1, null, null,
                "预警生成", triggerSnapshot);
        if (notifyService != null) {
            notifyService.notifyEventCreated(entity);
        }
        log.info("预警事件生成：{}（节点1=生成，触发快照已留痕）", entity.getEventNo());
        return entity.getId();
    }

    /** 确认（result=true 确认 / false 误报）；期望状态=1 */
    @Transactional
    public void confirm(long eventId, Long userId, String userName, boolean confirmed,
                        String conclusion) {
        int target = confirmed ? 2 : 6;
        int affected = warnEventMapper.updateOnConfirm(eventId, target, userId, conclusion);
        if (affected == 0) {
            throw new BizException(ErrorCode.B0302);
        }
        appendNode(eventId, confirmed ? NODE_CONFIRMED : NODE_FALSE_ALARM, 2, userId, userName,
                confirmed ? "确认预警" : "登记误报", conclusion);
    }

    /** 复核消警（期望状态=4；原因必填 B0304） */
    @Transactional
    public void close(long eventId, Long userId, String userName, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.B0304);
        }
        int affected = warnEventMapper.updateOnClose(eventId, userId, reason);
        if (affected == 0) {
            throw new BizException(ErrorCode.B0302);
        }
        appendNode(eventId, NODE_CLOSED, 2, userId, userName, "复核消警", reason);
    }

    /** 升级（新级别更高；detail 记 old→new，历史级别全留痕） */
    @Transactional
    public void upgrade(long eventId, Long userId, String userName, int newLevel, String reason) {
        WarnEventEntity current = warnEventMapper.selectById(eventId);
        if (current == null) {
            throw new BizException(ErrorCode.B0301);
        }
        int affected = warnEventMapper.updateLevelUp(eventId, newLevel);
        if (affected == 0) {
            throw new BizException(ErrorCode.B0302);
        }
        appendNode(eventId, NODE_UPGRADED, 2, userId, userName, "升级预警",
                "级别 " + current.getWarnLevel() + "→" + newLevel + "：" + reason);
        if (notifyService != null) {
            notifyService.notifyLevelChanged(eventId, current.getWarnLevel(), newLevel);
        }
    }

    /** 降级 */
    @Transactional
    public void downgrade(long eventId, Long userId, String userName, int newLevel, String reason) {
        WarnEventEntity current = warnEventMapper.selectById(eventId);
        if (current == null) {
            throw new BizException(ErrorCode.B0301);
        }
        int affected = warnEventMapper.updateLevelDown(eventId, newLevel);
        if (affected == 0) {
            throw new BizException(ErrorCode.B0302);
        }
        appendNode(eventId, NODE_DOWNGRADED, 2, userId, userName, "降级预警",
                "级别 " + current.getWarnLevel() + "→" + newLevel + "：" + reason);
        if (notifyService != null) {
            notifyService.notifyLevelChanged(eventId, current.getWarnLevel(), newLevel);
        }
    }

    /** 灾害登记关联（评审 4.3：一对多——全部关联事件各插一条"灾变确认"节点） */
    @Transactional
    public void linkHazard(long hazardEventId, List<Long> eventIds, Long userId, String userName) {
        for (Long eventId : eventIds) {
            warnEventMapper.updateHazardLink(eventId, hazardEventId);
            appendNode(eventId, NODE_HAZARD_CONFIRMED, 2, userId, userName,
                    "灾变确认", "关联灾害登记 #" + hazardEventId);
        }
        log.info("灾害登记关联完成：hazardId={} events={}", hazardEventId, eventIds);
    }

    /** 时间线查询（API-C03） */
    /** 事件详情（API-C02/C03/C20 出口校验用；不存在 B0301） */
    public WarnEventEntity getById(long eventId) {
        WarnEventEntity event = warnEventMapper.selectById(eventId);
        if (event == null) {
            throw new BizException(ErrorCode.B0301);
        }
        return event;
    }

    public List<TimelineEntity> timeline(long eventId) {
        return timelineMapper.selectByEvent(eventId);
    }

    /** 追加通知节点（NotifyService 调用，T-604） */
    public void appendNotifyNode(long eventId, String channelDesc) {
        appendNode(eventId, NODE_NOTIFIED, 1, null, null, "通知发送", channelDesc);
    }

    /** 派单节点5（DisposeTaskService 调用） */
    public void appendDisposeNode(long eventId, Long userId, String userName, String measure) {
        appendNode(eventId, NODE_DISPATCHED, 2, userId, userName, "处置派单", measure);
    }

    /** 待复核节点9（DisposeTaskService 调用） */
    /** 反馈节点6（DisposeTaskService 调用） */
    public void appendFeedbackNode(long eventId, Long userId, String userName, String content) {
        appendNode(eventId, NODE_FEEDBACK, 2, userId, userName, "处置反馈", content);
    }

    public void appendPendingReviewNode(long eventId, Long userId, String userName) {
        appendNode(eventId, NODE_PENDING_REVIEW, 2, userId, userName, "处置完成待复核", null);
    }

    private void appendNode(long eventId, int nodeType, int actorType,
                            Long actorId, String actorName, String action, String detail) {
        TimelineEntity node = new TimelineEntity();
        node.setEventId(eventId);
        node.setNodeType(nodeType);
        node.setActorType(actorType);
        node.setActorId(actorId);
        node.setActorName(actorName);
        node.setAction(action);
        node.setDetail(detail);
        node.setOccurTime(LocalDateTime.now(DB_ZONE));
        timelineMapper.insert(node);
    }
// ==================== T-708 API-C01/C04 ====================

    /** 预警事件分页（API-C01；scope 经 DataScopeHelper 构建） */
    public PageResult<WarnEventEntity> page(Long tunnelId, Integer level, Integer status,
                                            Integer hazardType, String keyword,
                                            LocalDateTime from, LocalDateTime to,
                                            int pageNum, int pageSize, DataScope scope) {
        DataScopeCondition cond = DataScopeHelper.forTunnel(scope);
        int offset = (pageNum - 1) * pageSize;
        List<WarnEventEntity> list = warnEventMapper.selectPage(
                tunnelId, level, status, hazardType, keyword, from, to, cond, offset, pageSize);
        long total = warnEventMapper.countPage(
                tunnelId, level, status, hazardType, keyword, from, to, cond);
        return new PageResult<>(total, list);
    }

    /** 预警统计（API-C04：按隧道×级别×对象聚合） */
    public List<java.util.Map<String, Object>> stats(LocalDateTime from, LocalDateTime to,
                                                     DataScope scope) {
        if (from == null || to == null) {
            throw new BizException(ErrorCode.A0002, "统计时间范围必填");
        }
        return warnEventMapper.stats(from, to, DataScopeHelper.forTunnel(scope));
    }}
