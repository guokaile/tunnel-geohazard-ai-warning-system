/**
 * 实时监控中心（F2）类型定义
 * 对齐《4.接口设计说明书》4.5.2 API-B01/B03/B05/B06/B16~B20 与后端 T-811 实际实现
 * （tgaws-business mon 域实体/VO，map-underscore-to-camel-case，字段名与后端 JSON 完全一致）。
 *
 * 与《4》差异（以后端实测为准）：
 * - B17/B18 时间入参名为 from/to（必填），《4》表格写作 start/end；
 * - B17 出参为裸数组 List<SeriesPointVo>（《4》示例的 {pointId,granularity,series} 包裹结构未实现）；
 * - B16/B17/B19 的 ts 为 ISO 串（yyyy-MM-ddTHH:mm:ss.SSS），展示层经 monMeta.formatDateTime 转 4.2 展示格式。
 */
import type { PageQuery, PageResult } from './api'

/* ---------------- 工程台账（B01/B03/B05/B06） ---------------- */

/** 隧道（API-B01，mon_tunnel 实体，出：全部启用隧道） */
export interface TunnelVO {
  id: number
  tunnelCode: string
  tunnelName: string
  /** 隧道类型 1公路 2铁路（字典 tunnel_type） */
  tunnelType?: number
  /** 工程阶段 1施工 2运营（字典 tunnel_stage） */
  stage?: number
  /** 1启用 0停用（point_status） */
  status?: number
  geoDesc?: string | null
  lengthM?: number | null
  startDate?: string | null
  remark?: string | null
  createTime?: string | null
  updateTime?: string | null
}

/** 断面（API-B03，mon_section 实体） */
export interface SectionVO {
  id: number
  sectionCode: string
  sectionName: string
  tunnelId: number
  mileageFrom?: string | null
  mileageTo?: string | null
  /** 地质分区描述 */
  geoZone?: string | null
  sort?: number
  stage?: number
  status?: number
  createTime?: string | null
  updateTime?: string | null
}

/** 点位台账（API-B05/B06，mon_point 实体；B06 实测仅台账字段，不含最新值/关联规则） */
export interface PointVO {
  id: number
  pointCode: string
  pointName: string
  tunnelId: number
  sectionId?: number | null
  /** 监测对象（字典 hazard_type 1~6） */
  hazardType: number
  /** 测项（字典 item_type，101~602 分组编码） */
  itemType: number
  unit?: string | null
  /** 小数缩放位（采集值 = 整数 × 10^-scale） */
  scale?: number
  /** 接入协议 1TCP 2MQTT（字典 data_source 同源口径） */
  protocol?: number
  /** 采集开关 1启用 0停用 */
  collectEnabled?: number
  /** 采集周期（秒） */
  collectFreqSec?: number
  gatewayCode?: string | null
  installPosition?: string | null
  rangeMin?: number | null
  rangeMax?: number | null
  /** 点位启停 1启用 0停用 */
  enabled?: number
  /** 1在线 0离线 */
  onlineStatus?: number
  /** 最近数据时间（ISO） */
  lastDataTime?: string | null
  remark?: string | null
  createTime?: string | null
  updateTime?: string | null
}

/** API-B05 点位分页入参（4.2 分页规范 + 台账过滤） */
export interface PointPageQuery extends PageQuery {
  tunnelId?: number
  sectionId?: number
  hazardType?: number
  /** 点位编码/名称模糊 */
  keyword?: string
  status?: number
}

/* ---------------- 实时监控查询（B16~B20） ---------------- */

/** 点位实时最新值（API-B16 列表项；B19 points 同结构） */
export interface PointLatestVO {
  pointId: number
  pointCode: string
  pointName: string
  tunnelId: number
  tunnelName?: string | null
  sectionId?: number | null
  hazardType: number
  itemType: number
  unit?: string | null
  /** 最新值（decimal(16,4) JSON number；无数据为 null） */
  value?: number | null
  /** 采样时间（ISO，毫秒） */
  ts?: string | null
  /** 质量位 0正常 1超范围 2跳变 3缺失；无数据为 null */
  quality?: number | null
  /** 1在线 0离线 */
  onlineStatus: number
  /** 当前预警级别 1蓝 2黄 3橙 4红；null=无预警 */
  warnLevel?: number | null
}

/** API-B16 入参（出参为裸数组，不分页） */
export interface LatestQuery {
  tunnelId?: number
  sectionId?: number
  hazardType?: number
  keyword?: string
}

/** 时序点（API-B17 列表项，data_sample 原始/聚合行） */
export interface SeriesPointVO {
  ts: string
  value: number
  quality: number
}

/** API-B17 入参 */
export interface SeriesQuery {
  /** 起止时间（后端 LocalDateTime.parse 仅接受 ISO 且必填；api 层统一 toIso 转换） */
  from: string
  to: string
  /** raw（原始，≤7 天，超限 B0116）| minute（分钟聚合） */
  granularity?: 'raw' | 'minute'
}

/** 统计值（API-B18；无样本时后端回全零而非 null） */
export interface PointStatsVO {
  max: number
  min: number
  avg: number
  /** 变化速率 = 值差/小时（后端按首末样本计算） */
  rate: number
}

/** 断面图数据（API-B19） */
export interface SectionBoardVO {
  sectionId: number
  sectionCode: string
  sectionName: string
  mileageFrom?: string | null
  mileageTo?: string | null
  geoZone?: string | null
  /** 断面点位分布 + 实时值（PointLatestVO 数组；无测点坐标字段，空间布局由前端推算） */
  points: PointLatestVO[]
}

/** 概览统计（API-B20） */
export interface OverviewVO {
  totalPoints: number
  onlinePoints: number
  offlinePoints: number
  /** 在线率 0~100（后端一位小数） */
  onlineRate: number
  /** 今日数据量（当日样本数） */
  todaySamples: number
}

/** 统一分页出参（4.2 分页规范） */
export type { PageResult }
