/**
 * 报表中心域类型（《4》4.5.5 API-F01~F05；字段与后端 DashboardService/ReportService VO 一致）
 */

/** 驾驶舱指标（API-F01，必传 tunnelId） */
export interface ReportDashboardVO {
  todayWarn: number
  openEvents: number
  closeRate: number
  gatewayOnlineRate: number
  sampleRate: number
  highRiskPoints: TopRiskPointVO[]
  trend: TrendPointVO[]
}

/** 高危点位（API-F01 出参） */
export interface TopRiskPointVO {
  pointCode: string
  pointName: string
  eventCount: number
  maxLevel: number
}

/** 7 日预警趋势点（API-F01 出参） */
export interface TrendPointVO {
  date: string
  warnTotal: number
}

/** 统计报表（API-F02：type=1日/2周/3月，period 日=YYYYMMDD 周=YYYYWW 月=YYYYMM） */
export interface ReportStatVO {
  warnTotal: number
  warnRed: number
  warnConfirmed: number
  warnClosed: number
  disposeTotal: number
  disposeClosed: number
  closeRate: number
  patrolTotal: number
  patrolDone: number
  patrolRate: number
  hazardNew: number
  hazardClosed: number
  sampleCount: number
  sampleExpect: number
  sampleRate: number
}

/** 分析报告（API-F03） */
export interface RptReportVO {
  id: number
  reportNo: string
  /** 1日 2周 3月 */
  reportType: number
  period: string
  filePath: string
  status: number
  generateTime: string
}
