/**
 * 监控域查询 API（《4.接口设计说明书》4.5.2 API-B01/B03/B05/B06/B16~B20；后端 T-811 已实现）
 *
 * 契约要点（与后端 MonController 实测核对）：
 * - B17/B18 时间入参名为 from/to（必填），与《4》表格中的 start/end 不一致，以后端为准；
 *   后端 LocalDateTime.parse 仅接受 ISO 格式，本模块统一 toIso 转换。
 * - B17 出参为裸数组 List<SeriesPointVo>（非《4》示例的 {pointId,granularity,series} 包裹）。
 * - B16 出参为裸数组（无分页），看板页按 tunnelId/sectionId 过滤以缩小 5s 轮询响应。
 * - B16/B17/B19 的 ts 为 ISO 串（毫秒），展示层用 monMeta.formatDateTime 转 4.2 展示格式。
 * - B01/B03 后端权限点为 mon:point:view（《4》表格写作"登录"），前端无需特判，403/C0008 由 http.ts 统一提示。
 */
import { get } from './http'
import type {
  LatestQuery,
  OverviewVO,
  PointLatestVO,
  PointPageQuery,
  PointStatsVO,
  PointVO,
  SectionBoardVO,
  SectionVO,
  SeriesPointVO,
  SeriesQuery,
  TunnelVO,
} from '@/types/mon'
import type { PageResult } from '@/types/api'

/** 空格分隔时间 → ISO（后端 LocalDateTime.parse 仅接受 ISO，与 warn 域同口径） */
function toIso(value?: string): string | undefined {
  if (!value) return undefined
  return value.includes('T') ? value : value.replace(' ', 'T')
}

/* ---------------- 工程台账 ---------------- */

/** API-B01 隧道列表（mon:point:view；出：全部启用隧道，按数据范围过滤） */
export function getTunnelListApi(): Promise<TunnelVO[]> {
  return get<TunnelVO[]>('/mon/tunnels')
}

/** API-B03 断面列表（mon:point:view；tunnelId 可空=范围内全部） */
export function getSectionListApi(tunnelId?: number): Promise<SectionVO[]> {
  return get<SectionVO[]>('/mon/sections', { tunnelId })
}

/** API-B05 点位分页（mon:point:view；tunnelId/sectionId/hazardType/keyword/status + 4.2 分页） */
export function getPointPageApi(params: PointPageQuery): Promise<PageResult<PointVO>> {
  return get<PageResult<PointVO>>('/mon/points', { ...params })
}

/** API-B06 点位详情（mon:point:view；越权 C0008。实测仅台账字段，不含最新值） */
export function getPointDetailApi(id: number): Promise<PointVO> {
  return get<PointVO>(`/mon/points/${id}`)
}

/* ---------------- 实时监控查询 ---------------- */

/** API-B16 实时最新值列表（mon:data:view；裸数组不分页，看板 5s 轮询数据源） */
export function getLatestListApi(params: LatestQuery = {}): Promise<PointLatestVO[]> {
  return get<PointLatestVO[]>('/mon/latest', { ...params })
}

/** API-B17 时序数据（mon:data:view；raw≤7 天超限 B0116 自动降级 minute；出：裸数组） */
export function getPointSeriesApi(id: number, params: SeriesQuery): Promise<SeriesPointVO[]> {
  return get<SeriesPointVO[]>(`/mon/points/${id}/series`, {
    from: toIso(params.from),
    to: toIso(params.to),
    granularity: params.granularity ?? 'raw',
  })
}

/** API-B18 统计值（mon:data:view；max/min/avg/rate，无样本回全零） */
export function getPointStatsApi(id: number, params: { from: string; to: string }): Promise<PointStatsVO> {
  return get<PointStatsVO>(`/mon/points/${id}/stats`, {
    from: toIso(params.from),
    to: toIso(params.to),
  })
}

/** API-B19 断面图数据（mon:data:view；断面元信息 + 点位分布实时值） */
export function getSectionBoardApi(sectionId: number): Promise<SectionBoardVO> {
  return get<SectionBoardVO>(`/mon/sections/${sectionId}/board`)
}

/** API-B20 概览统计（mon:data:view；点位总数/在线率/今日数据量，30s 轮询数据源） */
export function getOverviewApi(): Promise<OverviewVO> {
  return get<OverviewVO>('/mon/overview')
}
