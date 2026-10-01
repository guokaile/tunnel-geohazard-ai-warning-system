/**
 * 预警中心域 API（《4.接口设计说明书》4.5.3 API-C01~C20；后端 T-708 已实现）
 *
 * 契约要点（与后端 WarnController 核对）：
 * - 后端 LocalDateTime 入参用 LocalDateTime.parse（默认 ISO_LOCAL_DATE_TIME，不接受空格分隔），
 *   本模块统一 toIso 转换；出参为 ISO 串（2026-09-25T02:00:05[.SSS]），
 *   展示层用 warnMeta.formatDateTime 转 4.2 展示格式 yyyy-MM-dd HH:mm:ss。
 * - C15 出参为 {id}（任务 id，非 taskNo）；C18 出参为 {id}（反馈 id）。
 * - C16/C17/C19 权限点均为 warn:event:view / warn:event:confirm（与《4》4.5.3 表一致由后端注解控制）。
 */
import { del, get, post, put } from './http'
import type {
  DisposeFeedbackForm,
  DisposeTaskQuery,
  DisposeTaskVO,
  NotifyLogQuery,
  NotifyLogVO,
  WarnCloseForm,
  WarnConfirmForm,
  WarnDispatchForm,
  WarnEventQuery,
  WarnEventVO,
  WarnLevelChangeForm,
  WarnRuleForm,
  WarnRuleQuery,
  WarnRuleVO,
  WarnStatsRow,
  WarnTimelineNode,
} from '@/types/warn'
import type { PageResult } from '@/types/api'

/** 空格分隔时间 → ISO（后端 LocalDateTime.parse 仅接受 ISO 格式） */
function toIso(value?: string): string | undefined {
  if (!value) return undefined
  return value.includes('T') ? value : value.replace(' ', 'T')
}

/* ---------------- 预警事件 ---------------- */

/** API-C01 预警事件分页（warn:event:view）：tunnelId/level/status/hazardType/时间范围/keyword */
export function getWarnEventPageApi(params: WarnEventQuery): Promise<PageResult<WarnEventVO>> {
  return get<PageResult<WarnEventVO>>('/warn/events', {
    ...params,
    from: toIso(params.from),
    to: toIso(params.to),
  })
}

/** API-C02 事件详情（warn:event:view；数据出口按隧道校验，越权 C0008） */
export function getWarnEventDetailApi(id: number): Promise<WarnEventVO> {
  return get<WarnEventVO>(`/warn/events/${id}`)
}

/** API-C03 事件时间线（warn:event:view；FR-406 仅追加不可篡改） */
export function getWarnEventTimelineApi(id: number): Promise<WarnTimelineNode[]> {
  return get<WarnTimelineNode[]>(`/warn/events/${id}/timeline`)
}

/** API-C04 预警统计（warn:event:view）：from/to 必填（后端 A0002 校验），按隧道×级别×对象聚合 */
export function getWarnStatsApi(from: string, to: string): Promise<WarnStatsRow[]> {
  return get<WarnStatsRow[]>('/warn/events/stats', { from: toIso(from), to: toIso(to) })
}

/** API-C11 确认/误报（warn:event:confirm；状态机：状态=1 待确认，result 1→2已确认 / 2→6误报关闭） */
export function confirmWarnEventApi(id: number, data: WarnConfirmForm): Promise<null> {
  return post<null>(`/warn/events/${id}/confirm`, data)
}

/** API-C12 人工升级（warn:event:updown；仅新级别更高且状态 1/2/3，时间线节点7留痕） */
export function upgradeWarnEventApi(id: number, data: WarnLevelChangeForm): Promise<null> {
  return post<null>(`/warn/events/${id}/upgrade`, data)
}

/** API-C13 人工降级（warn:event:updown；仅新级别更低且状态 1/2/3，时间线节点8留痕） */
export function downgradeWarnEventApi(id: number, data: WarnLevelChangeForm): Promise<null> {
  return post<null>(`/warn/events/${id}/downgrade`, data)
}

/** API-C14 复核消警（warn:event:close；状态=4 待复核，reason 必填 B0304，FR-404） */
export function closeWarnEventApi(id: number, data: WarnCloseForm): Promise<null> {
  return post<null>(`/warn/events/${id}/close`, data)
}

/* ---------------- 处置闭环 ---------------- */

/** API-C15 创建处置任务（warn:event:dispatch；事件 2/3→3 处置中 + 时间线节点5）；出 {id} */
export function dispatchTaskApi(data: WarnDispatchForm): Promise<number> {
  return post<{ id: number }>('/warn/tasks', { ...data, deadline: toIso(data.deadline) }).then(
    (res) => res.id,
  )
}

/** API-C16 处置任务分页（warn:event:view；assigneeId 可选=我的待办，status 过滤） */
export function getDisposeTaskPageApi(params: DisposeTaskQuery): Promise<PageResult<DisposeTaskVO>> {
  return get<PageResult<DisposeTaskVO>>('/warn/tasks', { ...params })
}

/** API-C17 开始处置（warn:event:confirm；仅任务责任人，状态 1→2） */
export function startDisposeTaskApi(id: number): Promise<null> {
  return put<null>(`/warn/tasks/${id}/start`)
}

/**
 * API-C18 处置反馈（warn:event:confirm；仅处置中状态2，可分次，FR-403 + 时间线节点6）。
 * 注意：后端以 String.valueOf(body.images) 直接落库 varchar——前端若直接传 JS 数组，
 * 会得到 "[a, b]" 这种非 JSON 字符串，故此处先 JSON.stringify 序列化。
 */
export function feedbackDisposeTaskApi(id: number, data: DisposeFeedbackForm): Promise<number> {
  return post<{ id: number }>(`/warn/tasks/${id}/feedback`, {
    content: data.content,
    images: data.images?.length ? JSON.stringify(data.images) : undefined,
  }).then((res) => res.id)
}

/** API-C19 完成处置（warn:event:confirm；状态 2→3，事件 3→4 待复核 + 时间线节点9） */
export function finishDisposeTaskApi(id: number): Promise<null> {
  return put<null>(`/warn/tasks/${id}/finish`)
}

/* ---------------- 规则管理 ---------------- */

/** API-C05 规则列表（warn:rule:view；出每规则最新版本，不分页） */
export function getRuleListApi(params: WarnRuleQuery = {}): Promise<WarnRuleVO[]> {
  return get<WarnRuleVO[]>('/warn/rules', { ...params })
}

/** API-C06 新增规则（warn:rule:edit；expressionJson 校验合法 JSON，版本 1，状态启用） */
export function createRuleApi(data: WarnRuleForm): Promise<number> {
  return post<{ id: number }>('/warn/rules', data).then((res) => res.id)
}

/** API-C07 修改规则（warn:rule:edit；版本+1 新行，历史版本留痕） */
export function updateRuleApi(id: number, data: WarnRuleForm): Promise<number> {
  return put<{ id: number }>(`/warn/rules/${id}`, data).then((res) => res.id)
}

/** API-C08 删除规则（warn:rule:edit；被预警事件引用禁止删除 B0204，可改用停用） */
export function deleteRuleApi(id: number): Promise<null> {
  return del<null>(`/warn/rules/${id}`)
}

/** API-C09 启停规则（warn:rule:edit；status 1启用 / 0停用，停用规则不参与判定 B0203） */
export function updateRuleStatusApi(id: number, status: number): Promise<null> {
  return put<null>(`/warn/rules/${id}/status`, { status })
}

/** API-C10 规则版本历史（warn:rule:view；同 rule_code 全版本按版本号倒序） */
export function getRuleHistoryApi(id: number): Promise<WarnRuleVO[]> {
  return get<WarnRuleVO[]>(`/warn/rules/${id}/history`)
}

/* ---------------- 通知记录 ---------------- */

/** API-C20 通知记录查询（warn:event:view；eventId 必填，channelType/时间范围过滤） */
export function getNotifyLogPageApi(params: NotifyLogQuery): Promise<PageResult<NotifyLogVO>> {
  return get<PageResult<NotifyLogVO>>('/warn/notify-logs', {
    ...params,
    from: toIso(params.from),
    to: toIso(params.to),
  })
}
