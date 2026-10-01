/**
 * 预警中心（F3）类型定义
 * 对齐《4.接口设计说明书》4.5.3 API-C01~C20 与后端 T-708 实际实现
 * （tgaws-business warn 域实体 VO，map-underscore-to-camel-case，字段名与后端 JSON 完全一致）。
 */
import type { PageQuery, PageResult } from './api'

/* ---------------- 预警事件（API-C01/C02） ---------------- */

/** 预警事件列表项 / 详情（warn_event 实体；详情接口不返回 sectionId/ruleId 等列表扩展字段，故均置可选） */
export interface WarnEventVO {
  id: number
  /** 事件编号（yyMMdd+6位序号） */
  eventNo: string
  tunnelId: number
  sectionId?: number
  pointId?: number
  /** 监测对象（字典 hazard_type：1坍塌 2涌水/突水 3瓦斯及有害气体 4突泥 5地表沉降/拱顶下沉 6围岩收敛位移） */
  hazardType: number
  /** 测项（3 位分组编码，百位=监测对象） */
  itemType: number
  /** 级别 1蓝 2黄 3橙 4红（FR-302） */
  warnLevel: number
  warnTitle: string
  warnContent: string
  /** 命中规则 id（组合规则事件为空） */
  ruleId?: number
  /** 触发值快照 */
  triggerValue?: string
  /** 触发时间（后端 LocalDateTime ISO 串，展示层转 yyyy-MM-dd HH:mm:ss） */
  triggerTime: string
  /** 状态机：1待确认 2已确认 3处置中 4待复核 5已消警 6误报关闭（DB 3.7.2） */
  warnStatus: number
  /** 门禁阶段标记（独立于状态机）：0正常 1影子期 2灰度期 */
  gateStage?: number
  /** 关联灾害登记 id（FR-407 反向关联） */
  hazardEventId?: number
  createTime: string
}

/** API-C01 预警事件分页入参（4.2 分页规范 + tunnelId/level/status/hazardType/时间范围/keyword） */
export interface WarnEventQuery extends PageQuery {
  tunnelId?: number
  level?: number
  status?: number
  hazardType?: number
  keyword?: string
  /** 时间范围起止（后端 LocalDateTime.parse 仅接受 ISO，api 层统一转换） */
  from?: string
  to?: string
}

/** 事件时间线节点（API-C03，warn_event_timeline 仅追加不可篡改，FR-406 落地点） */
export interface WarnTimelineNode {
  id: number
  eventId: number
  /** 节点类型：1生成 2通知 3确认 4误报 5派单 6反馈 7升级 8降级 9待复核 10消警 11灾变确认 */
  nodeType: number
  /** 1系统 2人工 */
  actorType: number
  actorId?: number
  /** 操作人快照（防用户改名后失真） */
  actorName?: string
  action: string
  /** 详情（含触发值快照，不可被后续覆盖） */
  detail?: string
  occurTime: string
}

/** API-C04 预警统计行：后端 GROUP BY tunnel_id, warn_level, hazard_type */
export interface WarnStatsRow {
  tunnelId: number
  level: number
  hazardType: number
  cnt: number
}

/* ---------------- 预警规则（API-C05~C10） ---------------- */

/** 规则（mon_rule 实体；版本化：修改=同 rule_code 新版本行） */
export interface WarnRuleVO {
  id: number
  ruleCode: string
  ruleName: string
  /** 适用监测对象（0=全部） */
  hazardType: number
  /** 适用测项（0=全部） */
  itemType: number
  /** 适用工程阶段（0=全阶段） */
  stage: number
  /** 适用断面（0=全部） */
  sectionId: number
  /** 1阈值上限 2阈值下限 3速率 4突变 5组合 */
  ruleType: number
  /** 命中定级 1蓝 2黄 3橙 4红 */
  warnLevel: number
  /** 条件表达式/参数 JSON（后端仅校验合法 JSON 结构，Aviator 编译在引擎内） */
  expressionJson: string
  priority: number
  version: number
  /** 1启用 0停用 */
  status: number
  remark?: string
}

/** API-C05 规则列表入参（不分页，出每规则最新版本） */
export interface WarnRuleQuery {
  hazardType?: number
  itemType?: number
  status?: number
}

/** API-C06/C07 规则新增/修改入参（后端 RuleCmd 字段集） */
export interface WarnRuleForm {
  ruleName: string
  hazardType?: number
  itemType?: number
  stage?: number
  sectionId?: number
  ruleType: number
  warnLevel: number
  expressionJson: string
  priority?: number
  remark?: string
}

/* ---------------- 事件操作（API-C11~C15） ---------------- */

/** API-C11 确认/误报（FR-401）：result 1确认 / 2误报，conclusion 必填 */
export interface WarnConfirmForm {
  result: 1 | 2
  conclusion: string
}

/** API-C12/C13 人工升级/降级（留痕）：newLevel/reason */
export interface WarnLevelChangeForm {
  newLevel: number
  reason: string
}

/** API-C14 复核消警（FR-404）：reason 必填（后端 B0304），状态=待复核 */
export interface WarnCloseForm {
  reason: string
}

/** API-C15 创建处置任务（FR-402）：事件 2/3 → 3 处置中 */
export interface WarnDispatchForm {
  eventId: number
  /** 责任人用户 id（sys 域 API-A06 未实现前以 ID 输入） */
  assigneeId: number
  measure: string
  /** 完成时限（api 层转 ISO 提交） */
  deadline: string
}

/* ---------------- 处置任务（API-C16~C19） ---------------- */

/** 处置任务（warn_dispose_task 实体） */
export interface DisposeTaskVO {
  id: number
  taskNo: string
  eventId: number
  assigneeId: number
  assignerId: number
  measure: string
  deadline: string
  /** 1待处置 2处置中 3已完成 4超时未完成（超时由定时任务判定） */
  status: number
  finishTime?: string
}

/** API-C16 处置任务分页入参（assigneeId 可选=我的待办；C16 权限点 warn:event:view） */
export interface DisposeTaskQuery extends PageQuery {
  assigneeId?: number
  status?: number
}

/** API-C18 处置反馈（FR-403 分次反馈；仅处置中；提交时 images 序列化为 JSON 数组字符串） */
export interface DisposeFeedbackForm {
  content: string
  /** 图片路径数组（multipart 文件上传 W8 对象存储接入后启用，本期为路径文本输入） */
  images?: string[]
}

/* ---------------- 通知记录（API-C20） ---------------- */

/** 通知记录（warn_notify_log 实体，含 Outbox 投递字段） */
export interface NotifyLogVO {
  id: number
  eventId: number
  /** 1声光 2短信 3站内 */
  channelType: number
  /** 接收对象（人/报警器编号） */
  target: string
  content: string
  /** 1成功 2失败 3重试中 */
  status: number
  /** Outbox 投递状态：0待投递 1已投递 2失败待补偿 3已放弃 */
  deliverState: number
  attemptCount: number
  nextRetryTime?: string
  failReason?: string
}

/** API-C20 通知记录查询入参（eventId 必填，后端按事件隧道做数据权限校验） */
export interface NotifyLogQuery extends PageQuery {
  eventId: number
  channelType?: number
  from?: string
  to?: string
}

/** 统一分页出参（4.2 分页规范） */
export type { PageResult }
