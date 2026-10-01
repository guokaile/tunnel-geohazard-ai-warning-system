/**
 * 实时监控中心（F2）展示元数据（单一事实来源）
 *
 * - 四级色标（FR-302 / 需求 1.6.3 蓝黄橙红）：与 F3 预警中心 warnMeta.WARN_LEVEL_META 保持同一套色值
 *   （蓝 #409EFF / 黄 #E6A23C / 橙 #B45309 / 红 #F56C6C；橙经 ΔE 校验避免与黄混淆），保证跨页面一致。
 *   正常=绿 #67C23A、离线=灰 #909399；全部色标均带文字标签（tag/图例），不单靠颜色传达。
 * - 字典映射（DB 3.7.1）：hazard_type / item_type / quality。
 * - 多测项对比曲线色板：dataviz validate_palette 六项校验通过（相邻 CVD ΔE≥9.1 / 正常视觉 ≥19.6，
 *   低对比 3 槽由图例 + 统计表兜底），8 槽固定顺序不轮换——颜色跟随点位，不随选择数量重排。
 */
import { useAuthStore } from '@/stores/auth'

/** Element Plus tag type 联合 */
export type TagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'

/* ---------------- 枚举元数据（后端 tinyint/smallint 枚举，见 DB 3.7.1） ---------------- */

/** 预警四级色标（1蓝 2黄 3橙 4红；与 F3 warnMeta 同色值） */
export const WARN_LEVEL_META: Record<number, { label: string; color: string }> = {
  1: { label: '蓝', color: '#409EFF' },
  2: { label: '黄', color: '#E6A23C' },
  3: { label: '橙', color: '#B45309' },
  4: { label: '红', color: '#F56C6C' },
}

export function levelLabel(level: number): string {
  return WARN_LEVEL_META[level]?.label ?? String(level)
}

/** 预警级别彩色 tag 样式（深色底白字，级别色不可复用为普通 tag type） */
export function levelTagStyle(level: number): Record<string, string> {
  const color = WARN_LEVEL_META[level]?.color ?? '#909399'
  return { backgroundColor: color, borderColor: color, color: '#ffffff' }
}

/** 三态色标：正常（有值无预警）/ 超限（按级别色）/ 离线（无值） */
export const NORMAL_COLOR = '#67C23A'
export const OFFLINE_COLOR = '#909399'

/** 灾种字典（hazard_type 1~6；筛选用不含"0=全部"语义） */
export const HAZARD_TYPE_META: Record<number, string> = {
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

/** 测项字典（item_type 101~602 分组编码，百位=监测对象） */
export const ITEM_TYPE_META: Record<number, string> = {
  101: '围岩位移',
  102: '锚杆轴力',
  103: '钢架应力',
  201: '涌水量',
  202: '水压',
  203: '地下水位',
  301: 'CH4浓度',
  302: 'CO浓度',
  303: 'H2S浓度',
  304: '风速',
  401: '泥水流量',
  402: '孔隙水压',
  501: '沉降量',
  502: '下沉速率',
  601: '净空收敛',
  602: '周边位移',
}

export function itemLabel(type: number): string {
  return ITEM_TYPE_META[type] ?? `测项${type}`
}

/** 质量位（quality：0正常 1超范围 2跳变 3缺失） */
export const QUALITY_META: Record<number, { label: string; tag: TagType }> = {
  0: { label: '正常', tag: 'success' },
  1: { label: '超范围', tag: 'warning' },
  2: { label: '跳变', tag: 'danger' },
  3: { label: '缺失', tag: 'info' },
}

/** 在线状态（onlineStatus：1在线 0离线） */
export const ONLINE_META: Record<number, { label: string; tag: TagType }> = {
  0: { label: '离线', tag: 'info' },
  1: { label: '在线', tag: 'success' },
}

/* ---------------- 时间/数值格式 ---------------- */

/** 后端 LocalDateTime 输出 ISO（yyyy-MM-ddTHH:mm:ss[.SSS]），转 4.2 展示格式 yyyy-MM-dd HH:mm:ss */
export function formatDateTime(iso?: string | null): string {
  if (!iso) return '-'
  return iso.replace('T', ' ').replace(/\.\d+$/, '')
}

/** 当前时间 HH:mm:ss（用于"最近刷新"提示） */
export function formatTimeNow(): string {
  const d = new Date()
  const pad = (n: number): string => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/** 监测值展示：decimal(16,4) 去尾零保留至 4 位小数；null → '-' */
export function formatValue(value?: number | null): string {
  if (value == null) return '-'
  return String(Number(value.toFixed(4)))
}

/* ---------------- 多测项对比曲线色板（dataviz 校验通过） ---------------- */

/** 8 槽固定顺序（slot 1→8），颜色跟随点位实体而非选择次序 */
export const CURVE_PALETTE: string[] = [
  '#2a78d6', // slot1 蓝
  '#eb6834', // slot2 橙
  '#1baf7a', // slot3 青绿
  '#eda100', // slot4 黄
  '#e87ba4', // slot5 品红
  '#008300', // slot6 绿
  '#4a3aa7', // slot7 紫
  '#e34948', // slot8 红
]

/** 单图最多对比曲线数（= 色板槽位上限；超出的点位拒绝选择并提示） */
export const CURVE_MAX = CURVE_PALETTE.length

/* ---------------- 权限点显隐 ---------------- */

/**
 * 权限点显隐判定（4.7 权限点定义清单）。
 * TODO[契约]：后端 API-A01 登录响应 user 未返回 permissions（实测仅 id/username/realName/phone），
 * 且未实现 API-A04 GET /auth/me——前端拿不到权限点数组。缺失时视为"权限未知"→ 显示，
 * 由后端 @RequirePermission 兜底拒绝（HTTP 403 / 业务码 C0008 全局提示）。后端补齐后自动切换为严格显隐。
 * 与 F3 warnMeta.hasWarnPerm 同口径。
 */
export function hasMonPerm(perm: string): boolean {
  const perms = useAuthStore().user?.permissions
  if (!perms || perms.length === 0) return true
  return perms.includes(perm)
}
