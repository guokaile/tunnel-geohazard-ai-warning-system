/**
 * 权限点静态模拟数据（fallback）
 *
 * TODO：数据来源于《4.接口设计说明书》4.7 权限点定义清单（sys_permission 概念数据）。
 *      后端 GET /api/v1/sys/permissions（API-A18）就绪后，RoleManageView 中对该文件的
 *      fallback 引用应移除，本文件随之删除。
 */
import type { PermissionNode } from '@/types/api'

interface StaticPerm {
  permCode: string
  permName: string
}

interface PermGroup {
  permCode: string
  permName: string
  perms: StaticPerm[]
}

const GROUPS: PermGroup[] = [
  {
    permCode: 'sys',
    permName: '系统管理',
    perms: [
      { permCode: 'sys:user:view', permName: '用户查看' },
      { permCode: 'sys:user:edit', permName: '用户管理' },
      { permCode: 'sys:role:view', permName: '角色查看' },
      { permCode: 'sys:role:edit', permName: '角色管理' },
      { permCode: 'sys:dict:edit', permName: '字典管理' },
      { permCode: 'sys:log:view', permName: '审计日志查看' },
      { permCode: 'sys:channel:edit', permName: '通知通道配置' },
    ],
  },
  {
    permCode: 'mon',
    permName: '工程与点位',
    perms: [
      { permCode: 'mon:tunnel:edit', permName: '隧道维护' },
      { permCode: 'mon:section:edit', permName: '断面维护' },
      { permCode: 'mon:gateway:view', permName: '网关查看' },
      { permCode: 'mon:gateway:edit', permName: '网关维护' },
      { permCode: 'mon:point:view', permName: '点位台账查看' },
      { permCode: 'mon:point:edit', permName: '点位维护' },
      { permCode: 'mon:import:exec', permName: '数据导入' },
      { permCode: 'mon:data:view', permName: '监测数据查看' },
      { permCode: 'mon:data:flag', permName: '数据异常标记' },
      { permCode: 'mon:data:export', permName: '数据导出' },
    ],
  },
  {
    permCode: 'warn',
    permName: '预警中心',
    perms: [
      { permCode: 'warn:event:view', permName: '预警事件查看' },
      { permCode: 'warn:event:confirm', permName: '预警确认/误报' },
      { permCode: 'warn:event:dispatch', permName: '处置派单' },
      { permCode: 'warn:event:close', permName: '复核消警' },
      { permCode: 'warn:event:updown', permName: '升级/降级' },
      { permCode: 'warn:hazard:edit', permName: '灾害险情登记' },
      { permCode: 'warn:rule:view', permName: '规则查看' },
      { permCode: 'warn:rule:edit', permName: '规则维护' },
    ],
  },
  {
    permCode: 'patrol',
    permName: '巡检管理',
    perms: [
      { permCode: 'patrol:plan:view', permName: '巡检计划查看' },
      { permCode: 'patrol:plan:edit', permName: '巡检计划维护' },
      { permCode: 'patrol:template:view', permName: '巡检模板查看' },
      { permCode: 'patrol:template:edit', permName: '巡检模板维护' },
      { permCode: 'patrol:task:view', permName: '巡检任务查看' },
      { permCode: 'patrol:task:fill', permName: '巡检填报' },
      { permCode: 'patrol:hazard:view', permName: '隐患查看' },
      { permCode: 'patrol:hazard:edit', permName: '隐患登记闭环' },
    ],
  },
  {
    permCode: 'ai',
    permName: 'AI 分析',
    perms: [
      { permCode: 'ai:model:view', permName: '模型查看' },
      { permCode: 'ai:model:edit', permName: '模型参数管理' },
    ],
  },
  {
    permCode: 'rpt',
    permName: '报表与驾驶舱',
    perms: [
      { permCode: 'rpt:view', permName: '报表查看' },
      { permCode: 'rpt:dashboard', permName: '驾驶舱查看' },
    ],
  },
]

/** 树形权限数据：分组节点 + 权限点叶子；字段与后端 PermissionEntity 对齐（permCode/permName），
 *  node-key 用 id（与 API-A16 的权限点 id 列表对齐） */
export const STATIC_PERMISSION_TREE: PermissionNode[] = GROUPS.map((group) => ({
  id: `group-${group.permCode}`,
  permCode: group.permCode,
  permName: group.permName,
  children: group.perms.map((perm) => ({
    id: perm.permCode,
    permCode: perm.permCode,
    permName: `${perm.permName}（${perm.permCode}）`,
  })),
}))
