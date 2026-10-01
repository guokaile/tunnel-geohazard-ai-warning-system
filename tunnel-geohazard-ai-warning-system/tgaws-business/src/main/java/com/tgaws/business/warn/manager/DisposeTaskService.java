package com.tgaws.business.warn.manager;

import com.tgaws.business.warn.entity.DisposeFeedbackEntity;
import com.tgaws.business.warn.entity.DisposeTaskEntity;
import com.tgaws.business.warn.mapper.DisposeTaskMapper;
import com.tgaws.business.warn.mapper.WarnEventMapper;
import com.tgaws.common.datascope.DataScope;
import com.tgaws.common.datascope.DataScopeCondition;
import com.tgaws.common.datascope.DataScopeHelper;
import com.tgaws.common.exception.BizException;
import com.tgaws.common.result.PageResult;
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
 * 处置任务服务（FR-402~404：派单/开始/完成/超时，闭环状态机 + 时间线节点）。
 */
@Service
public class DisposeTaskService {

    private static final Logger log = LoggerFactory.getLogger(DisposeTaskService.class);

    private static final ZoneId DB_ZONE = ZoneId.of("Asia/Shanghai");

    private final DisposeTaskMapper disposeTaskMapper;
    private final WarnEventMapper warnEventMapper;
    private final WarnEventService warnEventService;
    private final SnowflakeIdGenerator idGenerator = SnowflakeIdGenerator.defaultInstance();

    public DisposeTaskService(DisposeTaskMapper disposeTaskMapper, WarnEventMapper warnEventMapper,
                              WarnEventService warnEventService) {
        this.disposeTaskMapper = disposeTaskMapper;
        this.warnEventMapper = warnEventMapper;
        this.warnEventService = warnEventService;
    }

    /** 派单（FR-402）：事件 2/3 → 3 处置中 + 创建任务 + 时间线节点5 */
    @Transactional
    public long dispatch(long eventId, long assigneeId, long assignerId, String assignerName,
                         String measure, LocalDateTime deadline) {
        int affected = warnEventMapper.updateToDisposing(eventId);
        if (affected == 0) {
            throw new BizException(ErrorCode.B0302);
        }
        DisposeTaskEntity task = new DisposeTaskEntity();
        task.setTaskNo(LocalDate.now(DB_ZONE).format(DateTimeFormatter.ofPattern("yyMMdd"))
                + String.format("%06d", idGenerator.nextId() % 1_000_000L));
        task.setEventId(eventId);
        task.setAssigneeId(assigneeId);
        task.setAssignerId(assignerId);
        task.setMeasure(measure);
        task.setDeadline(deadline);
        task.setStatus(1);
        disposeTaskMapper.insert(task);
        warnEventService.appendDisposeNode(eventId, assignerId, assignerName, measure);
        log.info("处置派单：{} 事件 {} 责任人 {}", task.getTaskNo(), eventId, assigneeId);
        return task.getId();
    }

    /** 开始处置（1→2；仅任务责任人，T-708 API-C17） */
    @Transactional
    public void start(long taskId, long userId) {
        DisposeTaskEntity task = disposeTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.B0401);
        }
        if (task.getAssigneeId() != userId) {
            throw new BizException(ErrorCode.B0403);
        }
        if (disposeTaskMapper.updateOnStart(taskId) == 0) {
            throw new BizException(ErrorCode.B0402);
        }
    }

    /** 完成处置（2→3 + 事件 3→4 待复核 + 时间线节点9） */
    @Transactional
    public void finish(long taskId, long userId, String userName) {
        DisposeTaskEntity task = disposeTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.B0401);
        }
        if (disposeTaskMapper.updateOnFinish(taskId) == 0) {
            throw new BizException(ErrorCode.B0402);
        }
        if (warnEventMapper.updateToPendingReview(task.getEventId()) == 0) {
            throw new BizException(ErrorCode.B0302);
        }
        warnEventService.appendPendingReviewNode(task.getEventId(), userId, userName);
    }

    /** 超时扫描（web 调度 5min 驱动） */
    public int markOverdue() {
        return disposeTaskMapper.markOverdue(LocalDateTime.now(DB_ZONE));
    }

    /** 我的待办 */
    public List<DisposeTaskEntity> listByAssignee(long assigneeId, Integer status) {
        return disposeTaskMapper.selectByAssignee(assigneeId, status);
    }
// ==================== T-708 API-C16/C18 ====================

    /** 处置任务分页（API-C16：assigneeId 可选=我的待办/全部；scope 携带） */
    public PageResult<DisposeTaskEntity> page(Long assigneeId, Integer status,
                                              int pageNum, int pageSize, DataScope scope) {
        DataScopeCondition cond = DataScopeHelper.forTunnel(scope);
        int offset = (pageNum - 1) * pageSize;
        List<DisposeTaskEntity> list = disposeTaskMapper.selectPage(
                assigneeId, status, cond, offset, pageSize);
        long total = disposeTaskMapper.countPage(assigneeId, status, cond);
        return new PageResult<>(total, list);
    }

    /** 处置反馈（API-C18，FR-403 分次反馈：仅处置中 2 可反馈；时间线节点6） */
    @Transactional
    public long feedback(long taskId, long userId, String userName, String content, String images) {
        DisposeTaskEntity task = disposeTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.B0401);
        }
        if (task.getStatus() != 2) {
            throw new BizException(ErrorCode.B0402, "仅处置中的任务可反馈");
        }
        if (content == null || content.isBlank()) {
            throw new BizException(ErrorCode.A0002, "反馈内容必填");
        }
        DisposeFeedbackEntity entity = new DisposeFeedbackEntity();
        entity.setTaskId(taskId);
        entity.setFeedbackUserId(userId);
        entity.setContent(content);
        entity.setImages(images);
        disposeTaskMapper.insertFeedback(entity);
        warnEventService.appendFeedbackNode(task.getEventId(), userId, userName, content);
        return entity.getId();
    }}
