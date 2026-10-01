/**
 * 巡检管理 API（《4》4.5.4 API-D01~D14，后端 PatrolController 已实现）
 *
 * 契约要点（与后端实测核对）：
 * - 计划/模板列表出裸数组（不分页）；任务/隐患出 PageResult（pageNum/pageSize，4.2 分页口径）。
 * - 任务/隐患时间入参 dateFrom/dateTo 为 ISO 串（后端 LocalDateTime.parse 仅接受 ISO）。
 * - 填报记录 clientKey 每项唯一（离线补传幂等），前端以 crypto.randomUUID() 生成。
 */
import { del, get, post, put } from './http'
import type { PageResult } from '@/types/api'
import type {
  PatrolHazardVO,
  PatrolPlanVO,
  PatrolRecordVO,
  PatrolTaskVO,
  PatrolTemplateDetailVO,
  PatrolTemplateItemVO,
  PatrolTemplateVO,
} from '@/types/patrol'

/* ---------------- 巡检计划（API-D01/D02） ---------------- */

/** API-D01 计划列表（tunnelId 可空=范围内全部） */
export function getPlanListApi(tunnelId?: number): Promise<PatrolPlanVO[]> {
  return get<PatrolPlanVO[]>('/patrol/plans', { tunnelId })
}

/** API-D02 创建计划（planName/tunnelId/frequencyType/timeSlot/templateId/inspectorId 必填） */
export function createPlanApi(data: Record<string, unknown>): Promise<number> {
  return post<{ id: number }>('/patrol/plans', data).then((r) => r.id)
}

/** API-D02 修改计划（字段可空=不修改） */
export function updatePlanApi(id: number, data: Record<string, unknown>): Promise<null> {
  return put<null>(`/patrol/plans/${id}`, data)
}

/** API-D02 删除计划 */
export function deletePlanApi(id: number): Promise<null> {
  return del<null>(`/patrol/plans/${id}`)
}

/* ---------------- 巡检模板（API-D03~D06） ---------------- */

/** API-D03 模板列表 */
export function getTemplateListApi(): Promise<PatrolTemplateVO[]> {
  return get<PatrolTemplateVO[]>('/patrol/templates')
}

/** API-D04 模板详情（template + items） */
export function getTemplateDetailApi(id: number): Promise<PatrolTemplateDetailVO> {
  return get<PatrolTemplateDetailVO>(`/patrol/templates/${id}`)
}

/** API-D05 创建模板（items：itemName/checkContent/judgeStandard/sort） */
export function createTemplateApi(data: Record<string, unknown>): Promise<number> {
  return post<{ id: number }>('/patrol/templates', data).then((r) => r.id)
}

/** API-D05 修改模板（版本+1，历史任务引用旧版本快照） */
export function updateTemplateApi(id: number, data: Record<string, unknown>): Promise<number> {
  return put<{ id: number }>(`/patrol/templates/${id}`, data).then((r) => r.id)
}

/** API-D06 删除模板 */
export function deleteTemplateApi(id: number): Promise<null> {
  return del<null>(`/patrol/templates/${id}`)
}

/* ---------------- 巡检任务（API-D07~D10） ---------------- */

/** API-D07 任务分页（inspectorId 传当前登录用户时才允许，越权 C0008） */
export function getTaskPageApi(params: {
  inspectorId?: number
  status?: number
  dateFrom?: string
  dateTo?: string
  pageNum: number
  pageSize: number
}): Promise<PageResult<PatrolTaskVO>> {
  return get<PageResult<PatrolTaskVO>>('/patrol/tasks', { ...params })
}

/** API-D08 开始巡检（巡检人=登录用户，服务层校验任务责任人） */
export function startTaskApi(id: number): Promise<null> {
  return post<null>(`/patrol/tasks/${id}/start`)
}

/** 手动生成巡检任务（演示/补漏；date 缺省=次日 YYYY-MM-DD；uk_plan_time 幂等） */
export function generateTasksApi(date: string): Promise<{ created: number; skipped: number }> {
  return post<{ created: number; skipped: number }>('/patrol/tasks/generate', undefined, {
    params: { date },
  })
}

/** API-D09 填报巡检记录（items[] 批量，clientKey 每项唯一） */
export function fillTaskRecordsApi(
  id: number,
  data: {
    images?: string
    longitude?: number
    latitude?: number
    items: Array<{
      itemId?: number
      itemName: string
      clientKey: string
      judgeStandardSnapshot?: string
      result: number
      description?: string
    }>
  },
): Promise<PatrolRecordVO[]> {
  return post<PatrolRecordVO[]>(`/patrol/tasks/${id}/records`, data)
}

/** API-D10 完成巡检 */
export function finishTaskApi(id: number): Promise<null> {
  return put<null>(`/patrol/tasks/${id}/finish`)
}

/* ---------------- 巡检隐患（API-D11~D14 + 转灾害） ---------------- */

/** API-D11 隐患分页 */
export function getHazardPageApi(params: {
  tunnelId?: number
  status?: number
  level?: number
  pageNum: number
  pageSize: number
}): Promise<PageResult<PatrolHazardVO>> {
  return get<PageResult<PatrolHazardVO>>('/patrol/hazards', { ...params })
}

/** API-D12 隐患登记（source/title/hazardLevel 必填） */
export function registerHazardApi(data: Record<string, unknown>): Promise<number> {
  return post<{ id: number }>('/patrol/hazards', data).then((r) => r.id)
}

/** API-D13 隐患转处置（handlerId 必填，状态 1/2→2） */
export function assignHazardApi(id: number, handlerId: number): Promise<null> {
  return put<null>(`/patrol/hazards/${id}`, { handlerId })
}

/** API-D14 隐患闭环（closeRemark 必填，状态 1/2→3） */
export function closeHazardApi(id: number, closeRemark: string): Promise<null> {
  return put<null>(`/patrol/hazards/${id}/close`, { closeRemark })
}

/** 隐患转灾害登记（评审 3.5） */
export function convertHazardToDisasterApi(
  id: number,
  data: Record<string, unknown>,
): Promise<number> {
  return post<{ hazardEventId: number }>(`/patrol/hazards/${id}/convert-disaster`, data).then(
    (r) => r.hazardEventId,
  )
}

export type { PatrolTemplateItemVO }
