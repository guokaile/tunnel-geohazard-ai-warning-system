/**
 * 系统管理域 API（《4.接口设计说明书》4.5.1 API-A06 ~ A23）
 */
import { del, get, post, put, type RequestConfig } from './http'
import type {
  LoginLogVO,
  OperLogVO,
  PageQuery,
  PageResult,
  PermissionNode,
  SysRoleForm,
  SysRoleVO,
  SysUserCreateForm,
  SysUserUpdateForm,
  SysUserVO,
} from '@/types/api'

/* ---------------- 用户管理 ---------------- */

/** API-A06 用户分页：keyword/status/roleId + 4.2 分页规范 */
export interface UserPageQuery extends PageQuery {
  keyword?: string
  status?: number
  roleId?: number
}

export function getUserPageApi(params: UserPageQuery): Promise<PageResult<SysUserVO>> {
  return get<PageResult<SysUserVO>>('/sys/users', { ...params })
}

/** API-A07 新增用户：username/realName/phone/roleIds，初始密码由后端策略生成 */
export function addUserApi(data: SysUserCreateForm): Promise<number> {
  return post<number>('/sys/users', data)
}

/** API-A08 修改用户：realName/phone/roleIds */
export function updateUserApi(id: number, data: SysUserUpdateForm): Promise<null> {
  return put<null>(`/sys/users/${id}`, data)
}

/** API-A09 删除用户（逻辑删；后端校验：不能删除自己） */
export function deleteUserApi(id: number): Promise<null> {
  return del<null>(`/sys/users/${id}`)
}

/** API-A10 启停用户：status 1 启用 / 0 停用 */
export function updateUserStatusApi(id: number, status: number): Promise<null> {
  return put<null>(`/sys/users/${id}/status`, { status })
}

/** API-A11 重置密码（重置后强制首次登录改密） */
export function resetPasswordApi(id: number): Promise<null> {
  return post<null>(`/sys/users/${id}/reset-password`)
}

/* ---------------- 角色管理 ---------------- */

/** API-A12 角色列表（出：全部角色，不分页） */
export function getRoleListApi(): Promise<SysRoleVO[]> {
  return get<SysRoleVO[]>('/sys/roles')
}

/** API-A13 新增角色：roleCode/roleName/remark */
export function addRoleApi(data: SysRoleForm): Promise<number> {
  return post<number>('/sys/roles', data)
}

/** API-A14 修改角色 */
export function updateRoleApi(id: number, data: SysRoleForm): Promise<null> {
  return put<null>(`/sys/roles/${id}`, data)
}

/** API-A15 删除角色（后端校验无用户占用） */
export function deleteRoleApi(id: number): Promise<null> {
  return del<null>(`/sys/roles/${id}`)
}

/** API-A16 角色已分配权限：出权限点 id 列表（与后端 List<Long> 契约一致） */
export function getRolePermissionsApi(id: number, config?: RequestConfig): Promise<number[]> {
  return get<number[]>(`/sys/roles/${id}/permissions`, undefined, config)
}

/** API-A17 分配权限：permIds[]（权限点 id 列表） */
export function assignRolePermissionsApi(id: number, permIds: number[]): Promise<null> {
  return put<null>(`/sys/roles/${id}/permissions`, { permIds })
}

/** API-A18 权限树：出树形 */
export function getPermissionTreeApi(config?: RequestConfig): Promise<PermissionNode[]> {
  return get<PermissionNode[]>('/sys/permissions', undefined, config)
}

/* ---------------- 审计日志 ---------------- */

/**
 * API-A22 操作日志：入 user/module/时间范围 + 4.2 分页规范。
 * TODO[契约]：《4》4.5.1 未给出时间范围参数名与出参字段，暂定 startTime/endTime 与 OperLogVO 字段，后端实现后校准。
 */
export interface OperLogQuery extends PageQuery {
  user?: string
  module?: string
  startTime?: string
  endTime?: string
}

export function getOperLogPageApi(params: OperLogQuery): Promise<PageResult<OperLogVO>> {
  return get<PageResult<OperLogVO>>('/sys/logs/oper', { ...params })
}

/**
 * API-A23 登录日志：入参未单独定义，按 4.2 分页规范 + user 过滤。
 * TODO[契约]：出参字段待后端定义，暂用 LoginLogVO 概念字段。
 */
export interface LoginLogQuery extends PageQuery {
  user?: string
}

export function getLoginLogPageApi(params: LoginLogQuery): Promise<PageResult<LoginLogVO>> {
  return get<PageResult<LoginLogVO>>('/sys/logs/login', { ...params })
}
