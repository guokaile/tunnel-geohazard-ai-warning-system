/**
 * 巡检管理域类型（《4》4.5.4 API-D01~D14；字段与后端 Patrol*Entity 序列化一致）
 */

/** 巡检计划（API-D01） */
export interface PatrolPlanVO {
  id: number
  planNo: string
  planName: string
  tunnelId: number
  /** 1每班 2每日 3每周 */
  frequencyType: number
  timeSlot: string
  templateId: number
  inspectorId: number
  /** 1启用 0停用 */
  enabled: number
}

/** 巡检模板（API-D03） */
export interface PatrolTemplateVO {
  id: number
  templateNo: string
  templateName: string
  version: number
  status: number
  remark?: string
}

/** 巡检模板项（API-D04） */
export interface PatrolTemplateItemVO {
  id: number
  templateId: number
  itemName: string
  checkContent?: string
  judgeStandard?: string
  sort: number
}

/** 模板详情（API-D04 出参 {template, items}） */
export interface PatrolTemplateDetailVO {
  template: PatrolTemplateVO
  items: PatrolTemplateItemVO[]
}

/** 巡检任务（API-D07） */
export interface PatrolTaskVO {
  id: number
  taskNo: string
  planId: number
  tunnelId: number
  sectionId?: number
  inspectorId: number
  templateId: number
  planTime: string
  /** 1待巡检 2进行中 3已完成 4逾期 */
  status: number
  finishTime?: string
}

/** 巡检记录（API-D09 出参） */
export interface PatrolRecordVO {
  id: number
  taskId: number
  clientKey: string
  itemId?: number
  itemName: string
  judgeStandardSnapshot?: string
  /** 1正常 2异常 3不适用 */
  result: number
  description?: string
  images?: string
  longitude?: number
  latitude?: number
  recordTime: string
  recorderId: number
}

/** 巡检隐患（API-D11） */
export interface PatrolHazardVO {
  id: number
  hazardNo: string
  tunnelId: number
  sectionId?: number
  /** 1巡检发现 2人工上报 */
  source: number
  title: string
  description?: string
  /** 1蓝 2黄 3橙 4红 */
  hazardLevel: number
  images?: string
  /** 1待处置 2处置中 3已闭环 */
  status: number
  handlerId?: number
  discoverUserId: number
  discoverTime: string
  closeTime?: string
  closeRemark?: string
  longitude?: number
  latitude?: number
  taskId?: number
  recordId?: number
  hazardType?: number
}
