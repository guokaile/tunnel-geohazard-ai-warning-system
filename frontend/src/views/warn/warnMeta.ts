/**
 * 预警中心展示元数据与状态机显隐规则（F3 单一事实来源）
 *
 * - 四级色标（FR-302）：需求 1.6.3 只定义了蓝/黄/橙/红四级与响应动作，未规定 hex 色值。
 *   蓝/黄/红沿用 Element Plus 主题色（#409EFF/#E6A23C/#F56C6C）；橙经色盲/正常视觉
 *   ΔE 校验选定 #B45309——橙若取 #FF9900/#F08C00 与黄混淆（正常视觉 ΔE 仅 4.3/5.0，
 *   色盲 ΔE 1.5/3.2），#B45309 与黄正常 ΔE 16.2、色盲 ΔE 14.1，全部通过
 *   dataviz validate_palette 六项校验。所有色标均带文字标签（tag/图例/数据标签），不单靠颜色传达。
 * - 状态机操作显隐与后端条件 UPDATE 的 WHERE 完全一致（WarnEventMapper.xml / DisposeTaskService），
 *   后端仍以 B0302/B0402/B0403 兜底校验，前端显隐只是第一道过滤。
 */
import type { DisposeTaskVO, WarnEventVO } from '@/types/warn'
import { useAuthStore } from '@/stores/auth'

/** Element Plus tag type 联合 */
export type TagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'

/* ---------------- 枚举元数据（后端 tinyint 枚举，见 DB 3.7.1/3.7.2） ---------------- */

/** 预警四级色标（1蓝 2黄 3橙 4红） */
export const WARN_LEVEL_META: Record<number, { label: string; color: string }> = {
  1: { label: '蓝', color: '#409EFF' },
  2: { label: '黄', color: '#E6A23C' },
  3: { label: '橙', color: '#B45309' },
  4: { label: '红', color: '#F56C6C' },
}

export function levelLabel(level: number): string {
  return WARN_LEVEL_META[level]?.label ?? String(level)
}

/** 事件状态（warn_status 状态机：1→2→3→4→5；1→6） */
export const WARN_STATUS_META: Record<number, { label: string; tag: TagType }> = {
  1: { label: '待确认', tag: 'info' },
  2: { label: '已确认', tag: 'primary' },
  3: { label: '处置中', tag: 'warning' },
  4: { label: '待复核', tag: 'danger' },
  5: { label: '已消警', tag: 'success' },
  6: { label: '误报关闭', tag: 'info' },
}

/** 灾种字典（hazard_type；0 为规则"全部"语义，仅规则筛选/表单使用） */
export const HAZARD_TYPE_META: Record<number, string> = {
  0: '全部',
  1: '坍塌',
  2: '涌水/突水',
  3: '瓦斯及有害气体',
  4: '突泥',
  5: '地表沉降/拱顶下沉',
  6: '围岩收敛位移',
}

export function hazardLabel(type: number): string {
  return HAZARD_TYPE_META[type] ?? String(type)
}

/** 规则类型（rule_type：1阈值上限 2阈值下限 3速率 4突变 5组合，FR-301） */
export const RULE_TYPE_META: Record<number, string> = {
  1: '阈值上限',
  2: '阈值下限',
  3: '速率',
  4: '突变',
  5: '组合',
}

/** 处置任务状态（dispose_status：1待处置 2处置中 3已完成 4超时未完成） */
export const TASK_STATUS_META: Record<number, { label: string; tag: TagType }> = {
  1: { label: '待处置', tag: 'info' },
  2: { label: '处置中', tag: 'primary' },
  3: { label: '已完成', tag: 'success' },
  4: { label: '超时未完成', tag: 'danger' },
}

/** 通知通道（channel_type：1声光 2短信 3站内，FR-304 三级通道） */
export const CHANNEL_META: Record<number, string> = {
  1: '声光',
  2: '短信',
  3: '站内',
}

/** 通知发送状态（1成功 2失败 3重试中） */
export const NOTIFY_STATUS_META: Record<number, { label: string; tag: TagType }> = {
  1: { label: '成功', tag: 'success' },
  2: { label: '失败', tag: 'danger' },
  3: { label: '重试中', tag: 'warning' },
}

/** Outbox 投递状态（deliver_state：0待投递 1已投递 2失败待补偿 3已放弃） */
export const DELIVER_STATE_META: Record<number, { label: string; tag: TagType }> = {
  0: { label: '待投递', tag: 'info' },
  1: { label: '已投递', tag: 'success' },
  2: { label: '失败待补偿', tag: 'warning' },
  3: { label: '已放弃', tag: 'danger' },
}

/** 门禁阶段（gate_stage，独立于状态机：0正常 1影子期 2灰度期） */
export const GATE_STAGE_META: Record<number, string> = {
  0: '正常',
  1: '影子期',
  2: '灰度期',
}

/**
 * 时间线节点类型（node_type 1生成~11灾变确认）与展示样式。
 * 颜色设计意图：生成/派单/反馈/降级=主蓝（常规流转），确认/消警=成功绿（闭环向好），
 * 升级=橙（趋严），误报=灰（无效化），待复核=黄（待人工），灾变确认=红（最严重）。
 * icon 为全局注册的 Element Plus 图标组件名。
 */
export const TIMELINE_NODE_META: Record<number, { label: string; color: string; icon: string }> = {
  1: { label: '生成', color: '#409EFF', icon: 'Bell' },
  2: { label: '通知', color: '#909399', icon: 'Message' },
  3: { label: '确认', color: '#67C23A', icon: 'CircleCheck' },
  4: { label: '误报', color: '#909399', icon: 'CircleClose' },
  5: { label: '派单', color: '#409EFF', icon: 'Promotion' },
  6: { label: '反馈', color: '#409EFF', icon: 'EditPen' },
  7: { label: '升级', color: '#B45309', icon: 'Top' },
  8: { label: '降级', color: '#409EFF', icon: 'Bottom' },
  9: { label: '待复核', color: '#E6A23C', icon: 'Timer' },
  10: { label: '消警', color: '#67C23A', icon: 'CircleCheckFilled' },
  11: { label: '灾变确认', color: '#F56C6C', icon: 'WarningFilled' },
}

export function timelineNodeLabel(nodeType: number): string {
  return TIMELINE_NODE_META[nodeType]?.label ?? `节点${nodeType}`
}

/* ---------------- 时间格式 ---------------- */

/** 后端 LocalDateTime 输出 ISO（yyyy-MM-ddTHH:mm:ss[.SSS]），转 4.2 展示格式 yyyy-MM-dd HH:mm:ss */
export function formatDateTime(iso?: string | null): string {
  if (!iso) return '-'
  return iso.replace('T', ' ').replace(/\.\d+$/, '')
}

/* ---------------- 权限点显隐 ---------------- */

/**
 * 权限点显隐判定（4.7 权限点定义清单，按钮级控制）。
 * TODO[契约]：后端 API-A01 登录响应 user 未返回 roles/permissions（《4》4.5.1 约定应返回），
 * 且未实现 API-A04 GET /auth/me——前端无法拿到权限点数组。缺失时视为"权限未知"→ 显示按钮，
 * 由后端 @RequirePermission 兜底拒绝（HTTP 403 / 业务码 C0008 全局提示）。后端补齐后自动切换为严格显隐。
 */
export function hasWarnPerm(perm: string): boolean {
  const perms = useAuthStore().user?.permissions
  if (!perms || perms.length === 0) return true
  return perms.includes(perm)
}

/* ---------------- 状态机操作显隐（与后端条件 UPDATE 的 WHERE 一致） ---------------- */

/** 事件可执行操作（对应当前行按钮与详情抽屉操作区） */
export interface EventActions {
  confirm: boolean
  dispatch: boolean
  upgrade: boolean
  downgrade: boolean
  close: boolean
}

/**
 * 事件状态机显隐规则：
 * - 确认/误报：状态=1 待确认（updateOnConfirm WHERE warn_status=1）
 * - 派单：状态=2/3（updateToDisposing WHERE warn_status IN (2,3)，FR-402）
 * - 升级：状态 1/2/3 且级别未到顶（updateLevelUp WHERE warn_level < newLevel AND warn_status IN (1,2,3)）
 * - 降级：状态 1/2/3 且级别未到底（updateLevelDown 对称）
 * - 消警：状态=4 待复核（updateOnClose WHERE warn_status=4，FR-404）
 */
export function eventActionsOf(event: Pick<WarnEventVO, 'warnStatus' | 'warnLevel'>): EventActions {
  const s = event.warnStatus
  const active = s === 1 || s === 2 || s === 3
  return {
    confirm: s === 1,
    dispatch: s === 2 || s === 3,
    upgrade: active && event.warnLevel < 4,
    downgrade: active && event.warnLevel > 1,
    close: s === 4,
  }
}

/** 处置任务可执行操作 */
export interface TaskActions {
  start: boolean
  feedback: boolean
  finish: boolean
}

/**
 * 任务状态机显隐规则（DisposeTaskService）：
 * - 开始：状态=1 且当前用户=责任人（后端非责任人 B0403，1→2）
 * - 反馈：状态=2 且当前用户=责任人（后端仅处置中可反馈 B0402，可分次）
 * - 完成：状态=2 且当前用户=责任人（2→3，事件转 4 待复核）
 */
export function taskActionsOf(
  task: Pick<DisposeTaskVO, 'status' | 'assigneeId'>,
  currentUserId?: number,
): TaskActions {
  const isAssignee = currentUserId != null && task.assigneeId === currentUserId
  return {
    start: task.status === 1 && isAssignee,
    feedback: task.status === 2 && isAssignee,
    finish: task.status === 2 && isAssignee,
  }
}
