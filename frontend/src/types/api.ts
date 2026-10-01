/**
 * 全局 API 类型定义
 * 对齐《4.接口设计说明书》：
 * - 4.2 统一响应：{"code":"00000","message":"success","data":{...},"timestamp":...}，code 为 5 位字符串
 * - 4.2 分页：入参 pageNum(≥1)/pageSize(1~200)，出参 {"total":100,"list":[...]}
 * - 4.5.1 认证与用户（API-A 组）
 */

/** 统一响应包裹（HTTP 层解包前） */
export interface Result<T = unknown> {
  code: string
  message: string
  data: T
  timestamp: number
}

/** 分页入参（4.2 分页规范） */
export interface PageQuery {
  pageNum: number
  pageSize: number
}

/** 分页出参 */
export interface PageResult<T> {
  total: number
  list: T[]
}

/** 当前登录用户（API-A01/A04 出参 user 字段） */
export interface UserInfo {
  id: number
  username: string
  realName: string
  phone?: string
  /** 角色编码列表，如 ["DISPATCHER"] */
  roles?: string[]
  /** 权限点编码列表（4.7 权限点定义清单） */
  permissions?: string[]
}

/** 登录响应 data（API-A01） */
export interface LoginResult {
  token: string
  refreshToken: string
  /** 秒 */
  expiresIn: number
  user: UserInfo
}

/** 刷新令牌响应 data（API-A03）：新 token 对，旧 refresh 立即失效 */
export interface RefreshResult {
  token: string
  refreshToken: string
  expiresIn?: number
}

/** 用户分页列表项（API-A06）
 * TODO[契约]：《4》4.5.1 未逐字段定义 API-A06 出参，字段按 sys_user 概念表拟定，
 *            后端实现后需按实际 VO 校准（尤其 lastLoginTime 是否在用户列表返回）。
 */
export interface SysUserVO {
  id: number
  username: string
  realName: string
  phone?: string
  /** 1 启用 / 0 停用（API-A10 status 入参，枚举值待后端确认） */
  status: number
  roleIds?: number[]
  /** 展示用角色名称（后端聚合返回） */
  roleNames?: string[]
  /** 最近登录时间（yyyy-MM-dd HH:mm:ss），接口未定义时显示 "-" */
  lastLoginTime?: string
  createTime?: string
}

/** 新增用户入参（API-A07：username/realName/phone/roleIds，初始密码由后端策略生成） */
export interface SysUserCreateForm {
  username: string
  realName: string
  phone?: string
  roleIds?: number[]
}

/** 修改用户入参（API-A08：realName/phone/roleIds） */
export interface SysUserUpdateForm {
  realName: string
  phone?: string
  roleIds?: number[]
}

/** 角色（API-A12/A13/A14） */
export interface SysRoleVO {
  id: number
  roleCode: string
  roleName: string
  remark?: string
  /** 数据权限范围（4.7：1全部 2本隧道 3本断面 4仅本人），展示用 */
  dataScope?: number
  createTime?: string
}

/** 角色新增入参（API-A13：roleCode/roleName/remark） */
export interface SysRoleForm {
  roleCode: string
  roleName: string
  remark?: string
}

/** 权限树节点（API-A18 出参：树形；字段与后端 PermissionEntity 序列化一致） */
export interface PermissionNode {
  id: number | string
  /** 权限点编码，规则 {模块}:{资源}:{动作}（4.7） */
  permCode: string
  permName: string
  children?: PermissionNode[]
}

/** 操作日志（API-A22）
 * TODO[契约]：《4》4.5.1 仅给出入参 user/module/时间范围，未定义出参字段，
 *            以下按 sys_log_oper 概念拟定，后端实现后需校准。
 */
export interface OperLogVO {
  id: number
  /** 操作人用户名 */
  user: string
  /** 所属模块，如 auth / sys / warn */
  module: string
  /** 操作动作描述 */
  action: string
  /** 请求方法与路径 */
  method?: string
  path?: string
  /** 请求 IP */
  ip?: string
  /** 是否成功 */
  success: boolean
  /** 操作时间 yyyy-MM-dd HH:mm:ss */
  operateTime: string
}

/** 登录日志（API-A23）
 * TODO[契约]：《4》4.5.1 未定义出参字段，以下按 sys_log_login 概念拟定，后端实现后需校准。
 */
export interface LoginLogVO {
  id: number
  user: string
  ip?: string
  /** 1 成功 / 0 失败 */
  success: boolean
  /** 失败原因（错误码文案） */
  failReason?: string
  loginTime: string
}
