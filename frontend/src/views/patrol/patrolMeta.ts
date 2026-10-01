/**
 * 巡检管理展示元数据（后端 tinyint 枚举，见 01_dict.sql / schema DDL 注释）
 */
import type { TagType } from '@/views/warn/warnMeta'
import { WARN_LEVEL_META, hazardLabel } from '@/views/warn/warnMeta'

export type { TagType }

/** 巡检频次（patrol_plan.frequency_type：1每班 2每日 3每周） */
export const FREQ_META: Record<number, string> = {
  1: '每班',
  2: '每日',
  3: '每周',
}

/** 巡检任务状态（patrol_status：1待巡检 2进行中 3已完成 4逾期） */
export const TASK_STATUS_META: Record<number, { label: string; tag: TagType }> = {
  1: { label: '待巡检', tag: 'info' },
  2: { label: '进行中', tag: 'primary' },
  3: { label: '已完成', tag: 'success' },
  4: { label: '逾期', tag: 'danger' },
}

/** 巡检结果（patrol_result：1正常 2异常 3不适用） */
export const RESULT_META: Record<number, { label: string; tag: TagType }> = {
  1: { label: '正常', tag: 'success' },
  2: { label: '异常', tag: 'danger' },
  3: { label: '不适用', tag: 'info' },
}

/** 隐患状态（hazard_status：1待处置 2处置中 3已闭环） */
export const HAZARD_STATUS_META: Record<number, { label: string; tag: TagType }> = {
  1: { label: '待处置', tag: 'danger' },
  2: { label: '处置中', tag: 'warning' },
  3: { label: '已闭环', tag: 'success' },
}

/** 隐患来源（patrol_hazard.source：1巡检发现 2人工上报） */
export const HAZARD_SOURCE_META: Record<number, string> = {
  1: '巡检发现',
  2: '人工上报',
}

/** 隐患等级：复用预警四级色标（1蓝 2黄 3橙 4红，与 warnMeta 单一事实来源一致） */
export function hazardLevelLabel(level: number): string {
  return WARN_LEVEL_META[level]?.label ?? String(level)
}

export function hazardLevelColor(level: number): string {
  return WARN_LEVEL_META[level]?.color ?? '#909399'
}

export { hazardLabel }
