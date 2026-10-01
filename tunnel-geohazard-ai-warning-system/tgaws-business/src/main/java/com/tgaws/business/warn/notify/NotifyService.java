package com.tgaws.business.warn.notify;

import com.tgaws.business.warn.entity.NotifyLogEntity;
import com.tgaws.business.warn.entity.WarnEventEntity;
import com.tgaws.business.warn.manager.WarnEventService;
import com.tgaws.business.warn.mapper.NotifyLogMapper;
import com.tgaws.common.enums.GateStage;
import com.tgaws.common.enums.NotifyChannelType;
import com.tgaws.common.enums.WarnLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 通知服务（Outbox 模式，《6》6.3.3/6.7，评审 3.3/4.4）：
 *
 * <ul>
 *   <li><b>门禁拦截在本层</b>：shadow 只标记不通知；gray 蓝/黄通知、橙/红待人工确认后另行通知；
 *       live 全量通知——判定与落库不受门禁影响；</li>
 *   <li><b>Outbox</b>：通知记录在业务事务内落库（deliver_state=0），afterCommit 尽快投递；
 *       提交后崩溃由补偿任务兜底，杜绝"已提交未通知"静默丢失；</li>
 *   <li><b>补偿约束（评审 4.4）</b>：扫描→发送→更新**逐条独立**，不做大事务；
 *       attempt_count/next_retry_time 在**发送尝试之后**更新；</li>
 *   <li><b>退避</b>：10s/30s/2min/10min，上限 retryMax 次后置"已放弃"+D0003 升级告警；</li>
 *   <li><b>红级兜底</b>：同步尝试 1 次，T+10s 未确认投递 → 升级系统故障事件并通知管理员；</li>
 *   <li><b>短信/声光失败 2 次后转站内兜底</b>（文档 4.4.6 口径）。</li>
 * </ul>
 */
@Service
public class NotifyService {

    private static final Logger log = LoggerFactory.getLogger(NotifyService.class);

    private static final ZoneId DB_ZONE = ZoneId.of("Asia/Shanghai");

    private static final long[] BACKOFF_SECONDS = {10L, 30L, 120L, 600L};

    private final NotifyLogMapper notifyLogMapper;
    private final WarnEventService warnEventService;
    private final Map<NotifyChannelType, NotifyChannel> channels;
    private final TransactionTemplate transactionTemplate;

    private final int retryMax;
    private final int pendingMax;
    private final int pendingAlertSec;

    /** 领取窗口（秒）：claim 后 next_retry 推后窗口，任务崩溃后自然重入 */
    private static final long CLAIM_WINDOW_SECONDS = 60L;

    /** 红级投递确认跟踪（eventId → 首次尝试时刻） */
    private final Map<Long, Long> redEventFirstAttemptMs = new ConcurrentHashMap<>();

    // ==================== T-708 API-C20 通知记录查询 ====================

    /** 通知记录分页查询（eventId 必填；出口校验在 Controller 按事件隧道完成） */
    public com.tgaws.common.result.PageResult<NotifyLogEntity> listLogs(
            long eventId, Integer channelType, LocalDateTime from, LocalDateTime to,
            int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        java.util.List<NotifyLogEntity> list = notifyLogMapper.selectByFilter(
                eventId, channelType, from, to, offset, pageSize);
        long total = notifyLogMapper.countByFilter(eventId, channelType, from, to);
        return new com.tgaws.common.result.PageResult<>(total, list);
    }
    private final ScheduledExecutorService redEscalationScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "red-notify-escalation");
                t.setDaemon(true);
                return t;
            });

    public NotifyService(NotifyLogMapper notifyLogMapper,
                         @Lazy WarnEventService warnEventService,
                         PlatformTransactionManager transactionManager,
                         List<NotifyChannel> channelList,
                         @Value("${notify.retry_max:6}") int retryMax,
                         @Value("${notify.pending_max:100}") int pendingMax,
                         @Value("${notify.pending_alert_sec:60}") int pendingAlertSec) {
        this.notifyLogMapper = notifyLogMapper;
        this.warnEventService = warnEventService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.channels = channelList.stream()
                .collect(Collectors.toMap(NotifyChannel::type, c -> c));
        this.retryMax = retryMax;
        this.pendingMax = pendingMax;
        this.pendingAlertSec = pendingAlertSec;
    }

    /**
     * 事件创建后触发（在业务事务内调用：插入 Outbox 记录，afterCommit 投递）。
     * 门禁拦截（评审 3.3）：shadow 不通知；gray 橙/红不自动通知。
     */
    public void notifyEventCreated(WarnEventEntity event) {
        GateStage stage = event.getGateStage() == null ? GateStage.LIVE : mapStage(event.getGateStage());
        WarnLevel level = WarnLevel.of(event.getWarnLevel());
        if (stage == GateStage.SHADOW) {
            log.info("影子期只标记不通知：事件 {}", event.getEventNo());
            return;
        }
        if (stage == GateStage.GRAY && level.compareTo(WarnLevel.YELLOW) > 0) {
            log.info("灰度期橙/红需人工确认后另行通知：事件 {} 级别 {}", event.getEventNo(), level.getLabel());
            return;
        }
        List<NotifyLogEntity> records = new ArrayList<>();
        for (NotifyChannel channel : channels.values()) {
            NotifyLogEntity record = newRecord(event, channel.type(), channel.name());
            notifyLogMapper.insert(record);
            records.add(record);
        }
        log.info("Outbox 记录已落库（待投递 {} 条）：事件 {}", records.size(), event.getEventNo());
        // 红/橙级同步尝试 1 次；其余 afterCommit 尽快投递
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (NotifyLogEntity r : records) {
                        dispatch(r);
                    }
                    if (level == WarnLevel.RED) {
                        scheduleRedEscalation(event.getId(), records);
                    }
                }
            });
        } else {
            for (NotifyLogEntity r : records) {
                dispatch(r);
            }
        }
    }

    /** 补偿批处理（web 调度 30s 驱动；逐条独立，无包裹事务——评审 4.4） */
    public int dispatchPending(int limit) {
        List<NotifyLogEntity> pending = notifyLogMapper.selectPending(LocalDateTime.now(DB_ZONE), limit);
        for (NotifyLogEntity record : pending) {
            dispatch(record);
        }
        return pending.size();
    }

    /**
     * 领取式补偿（评审 3.3）：短事务内 SELECT FOR UPDATE SKIP LOCKED 锁定候选并
     * 标记领取窗口（next_retry 推后 60s），提交后逐条投递——并发/重入不重复投递，
     * 任务崩溃后 60s 自然重入。
     */
    public int claimAndDispatch(int limit) {
        List<NotifyLogEntity> claimed = transactionTemplate.execute(status -> {
            List<NotifyLogEntity> records =
                    notifyLogMapper.selectPendingForUpdate(LocalDateTime.now(DB_ZONE), limit);
            LocalDateTime claimUntil = LocalDateTime.now(DB_ZONE).plusSeconds(CLAIM_WINDOW_SECONDS);
            for (NotifyLogEntity record : records) {
                notifyLogMapper.markClaimed(record.getId(), claimUntil);
            }
            return records;
        });
        if (claimed == null) {
            return 0;
        }
        for (NotifyLogEntity record : claimed) {
            dispatch(record);
        }
        return claimed.size();
    }

    /** 积压哨兵（web 调度 1min 驱动；超阈值告警——系统"哑火"最后一道哨） */
    public void sentinel() {
        int pending = notifyLogMapper.countPending();
        LocalDateTime oldest = notifyLogMapper.oldestPendingTime();
        boolean oldestStale = oldest != null
                && oldest.isBefore(LocalDateTime.now(DB_ZONE).minusSeconds(pendingAlertSec));
        if (pending > pendingMax || oldestStale) {
            log.error("通知积压告警：待投递 {} 条（阈值 {}），最早待投递 {}（阈值 {}s）——疑似投递链路异常",
                    pending, pendingMax, oldest, pendingAlertSec);
        }
    }

    /** 投递单条（成功→delivered；失败→attempt+1 与 nextRetry 在发送之后更新） */
    void dispatch(NotifyLogEntity record) {
        if (record.getAttemptCount() != null && record.getAttemptCount() >= retryMax) {
            notifyLogMapper.updateAfterAttempt(record.getId(), 2, 3,
                    record.getAttemptCount(), null, "超过最大尝试次数（已放弃）");
            log.error("D0003 通知投递放弃：record={} channel={}", record.getId(), record.getChannelType());
            return;
        }
        NotifyChannel channel = channels.get(NotifyChannelType.of(record.getChannelType()));
        if (channel == null) {
            notifyLogMapper.updateAfterAttempt(record.getId(), 2, 3,
                    record.getAttemptCount(), null, "通道未启用");
            return;
        }
        try {
            channel.send(record.getTarget(), record.getContent());
            notifyLogMapper.updateDelivered(record.getId());
            warnEventService.appendNotifyNode(record.getEventId(), channel.name());
        } catch (Exception e) {
            int attempt = (record.getAttemptCount() == null ? 0 : record.getAttemptCount()) + 1;
            int state = attempt >= retryMax ? 3 : 2;
            LocalDateTime nextRetry = state == 3 ? null
                    : LocalDateTime.now(DB_ZONE).plusSeconds(backoffSeconds(attempt));
            notifyLogMapper.updateAfterAttempt(record.getId(), 2, state, attempt,
                    nextRetry, truncate(e.getMessage()));
            log.warn("通知发送失败（attempt={}/{}，下次重试 {}）：record={} channel={} err={}",
                    attempt, retryMax, nextRetry, record.getId(), channel.name(), e.getMessage());
            // 短信/声光失败 2 次后转站内兜底
            if (attempt == 2 && record.getChannelType() != NotifyChannelType.INNER.getValue()) {
                NotifyLogEntity fallback = newRecordFallback(record);
                notifyLogMapper.insert(fallback);
                log.info("转站内兜底：record={} → 站内消息", record.getId());
            }
        }
    }

    private void scheduleRedEscalation(long eventId, List<NotifyLogEntity> records) {
        redEventFirstAttemptMs.putIfAbsent(eventId, System.currentTimeMillis());
        redEscalationScheduler.schedule(() -> {
            // 评审 3.4 判据：红级至少 2 条通道独立成功（deliver_state=1）才算送达
            int delivered = notifyLogMapper.countDeliveredByEvent(eventId);
            if (delivered < 2) {
                log.error("D0009 红级通知未送达（eventId={}，已投递通道 {}/2）——升级系统故障并通知管理员",
                        eventId, delivered);
                NotifyLogEntity adminMsg = new NotifyLogEntity();
                adminMsg.setEventId(eventId);
                adminMsg.setChannelType(NotifyChannelType.INNER.getValue());
                adminMsg.setTarget("admin");
                adminMsg.setContent("系统故障：红级预警通知未送达（已投递 " + delivered + "/2 通道），请立即人工介入");
                notifyLogMapper.insert(adminMsg);
            }
        }, 10, TimeUnit.SECONDS);
    }

    /** 升级/降级通知（事件状态机调用；站内通道 + 时间线节点2） */
    public void notifyLevelChanged(long eventId, int oldLevel, int newLevel) {
        WarnLevel from = WarnLevel.of(oldLevel);
        WarnLevel to = WarnLevel.of(newLevel);
        NotifyLogEntity record = new NotifyLogEntity();
        record.setEventId(eventId);
        record.setChannelType(NotifyChannelType.INNER.getValue());
        record.setTarget("值班调度");
        record.setContent("预警级别变更：" + from.getLabel() + " → " + to.getLabel());
        record.setStatus(3);
        record.setDeliverState(0);
        record.setAttemptCount(0);
        notifyLogMapper.insert(record);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(record);
                }
            });
        } else {
            dispatch(record);
        }
    }

    /** 退避序列（包级可见供测试断言） */
    static long backoffSeconds(int attempt) {
        return BACKOFF_SECONDS[Math.min(Math.max(attempt, 1), BACKOFF_SECONDS.length) - 1];
    }

    private NotifyLogEntity newRecord(WarnEventEntity event, NotifyChannelType type, String channelName) {
        NotifyLogEntity record = new NotifyLogEntity();
        record.setEventId(event.getId());
        record.setChannelType(type.getValue());
        record.setTarget(type == NotifyChannelType.LIGHT_ALARM ? "ALM01" : "值班调度");
        record.setContent("[" + event.getWarnTitle() + "] " + event.getWarnContent());
        record.setStatus(3);
        record.setDeliverState(0);
        record.setAttemptCount(0);
        return record;
    }

    private NotifyLogEntity newRecordFallback(NotifyLogEntity source) {
        NotifyLogEntity record = new NotifyLogEntity();
        record.setEventId(source.getEventId());
        record.setChannelType(NotifyChannelType.INNER.getValue());
        record.setTarget("值班调度");
        record.setContent(source.getContent());
        record.setStatus(3);
        record.setDeliverState(0);
        record.setAttemptCount(0);
        return record;
    }

    private static String truncate(String msg) {
        return msg == null ? null : (msg.length() > 250 ? msg.substring(0, 250) : msg);
    }

    private static GateStage mapStage(int gateStage) {
        return switch (gateStage) {
            case 1 -> GateStage.SHADOW;
            case 2 -> GateStage.GRAY;
            default -> GateStage.LIVE;
        };
    }
}
