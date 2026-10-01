package com.tgaws.business.warn.mapper;

import com.tgaws.business.warn.entity.NotifyLogEntity;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知记录 Mapper（warn_notify_log：Outbox 状态机，逐条独立更新——评审 4.4）。
 */
public interface NotifyLogMapper {

    /** 业务事务内落库（deliver_state=0 待投递） */
    int insert(NotifyLogEntity entity);

    /** 补偿扫描：待投递/失败待补偿且到达重试时间（每批上限） */
    List<NotifyLogEntity> selectPending(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** 领取（评审 3.3）：SELECT FOR UPDATE SKIP LOCKED 锁定候选行——须在事务内调用 */
    List<NotifyLogEntity> selectPendingForUpdate(@Param("now") LocalDateTime now,
                                                 @Param("limit") int limit);

    /** 领取窗口标记：next_retry_time 推后 claimWindowSec（事务内与领取同批提交） */
    int markClaimed(@Param("id") long id, @Param("nextRetryTime") LocalDateTime nextRetryTime);

    /** 红级判据（评审 3.4）：事件已投递通道数 */
    int countDeliveredByEvent(@Param("eventId") long eventId);

    /**
     * 发送尝试后的逐条更新（条件：deliver_state IN (0,2)——并发安全，
     * attempt/next_retry 在发送之后更新，评审 4.4）。
     */
    int updateAfterAttempt(@Param("id") long id, @Param("status") int status,
                           @Param("deliverState") int deliverState,
                           @Param("attemptCount") int attemptCount,
                           @Param("nextRetryTime") LocalDateTime nextRetryTime,
                           @Param("failReason") String failReason);

    /** 投递成功 */
    int updateDelivered(@Param("id") long id);

    /** 积压统计 */
    int countPending();

    /** 最早待投递时间（积压哨兵） */
    LocalDateTime oldestPendingTime();

    // ---------- T-708 API-C20 通知记录过滤查询 ----------

    List<NotifyLogEntity> selectByFilter(@Param("eventId") long eventId,
                                         @Param("channelType") Integer channelType,
                                         @Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    long countByFilter(@Param("eventId") long eventId,
                       @Param("channelType") Integer channelType,
                       @Param("from") LocalDateTime from,
                       @Param("to") LocalDateTime to);
}
