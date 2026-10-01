/**
 * 报表中心 API（《4》4.5.5 API-F01~F05，后端 RptController 已实现）
 *
 * 契约要点（与后端实测核对）：
 * - F01 必传 tunnelId；F02 period 格式：日=YYYYMMDD 周=YYYYWW 月=YYYYMM（否则 A0002）。
 * - F05 下载出二进制流，走 http 实例 responseType=blob（拦截器对非 JSON 原样透传）。
 */
import { downloadBlob, get, post } from './http'
import type { ReportDashboardVO, ReportStatVO, RptReportVO } from '@/types/rpt'

/** API-F01 驾驶舱指标（rpt:dashboard；tunnelId 必填） */
export function getReportDashboardApi(tunnelId: number): Promise<ReportDashboardVO> {
  return get<ReportDashboardVO>('/rpt/dashboard', { tunnelId })
}

/** API-F02 统计报表（type=1日/2周/3月 + period） */
export function getReportStatisticsApi(params: {
  type: number
  period: string
  tunnelId?: number
}): Promise<ReportStatVO> {
  return get<ReportStatVO>('/rpt/statistics', { ...params })
}

/** API-F03 分析报告列表 */
export function getReportListApi(params?: {
  type?: number
  period?: string
}): Promise<RptReportVO[]> {
  return get<RptReportVO[]>('/rpt/reports', { ...params })
}

/** API-F04 手动生成报告 */
export function generateReportApi(type: number, period: string): Promise<number> {
  return post<{ id: number }>('/rpt/reports/generate', undefined, {
    params: { type, period },
  }).then((r) => r.id)
}

/** API-F05 下载报告（Blob；文件名从 Content-Disposition 解析，失败回退 reportNo） */
export async function downloadReportApi(
  id: number,
  fallbackName: string,
): Promise<{ blob: Blob; filename: string }> {
  const response = await downloadBlob(`/rpt/reports/${id}/download`)
  const blob = response.data as Blob
  const disposition = String(response.headers['content-disposition'] ?? '')
  const match = /filename="?([^";]+)"?/i.exec(disposition)
  return { blob, filename: match?.[1] ?? `${fallbackName}.html` }
}
