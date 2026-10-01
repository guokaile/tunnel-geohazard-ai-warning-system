/**
 * 认证域 API（《4.接口设计说明书》4.5.1 API-A01 ~ A05）
 */
import { ApiError, get, post, put, rawHttp, SUCCESS_CODE } from './http'
import type { LoginResult, RefreshResult, Result, UserInfo } from '@/types/api'

/** API-A01 登录：入 username/password；出 token/refreshToken/user（含角色+权限点） */
export function loginApi(data: { username: string; password: string }): Promise<LoginResult> {
  // 登录失败提示由登录页取后端 message 展示，跳过全局提示避免重复
  return post<LoginResult>('/auth/login', data, { skipErrorMessage: true })
}

/** API-A02 登出（令牌进黑名单，TTL 30min） */
export function logoutApi(): Promise<null> {
  return post<null>('/auth/logout', undefined, { skipErrorMessage: true })
}

/**
 * API-A03 刷新令牌（轮换）：旧 refresh 立即失效。
 * 走裸实例（rawHttp），不经过主拦截器，避免刷新请求本身进入 401 重试递归。
 */
export function refreshApi(data: { refreshToken: string }): Promise<RefreshResult> {
  return rawHttp.post<Result<RefreshResult>>('/auth/refresh', data).then((res) => {
    const body = res.data
    if (body?.code === SUCCESS_CODE && body.data) {
      return body.data
    }
    throw new ApiError(body?.code ?? 'C0006', body?.message || '刷新令牌失败')
  })
}

/** API-A04 当前用户信息：用户+角色+权限点 */
export function meApi(): Promise<UserInfo> {
  return get<UserInfo>('/auth/me')
}

/** API-A05 修改本人密码（校验复杂度+历史） */
export function changePasswordApi(data: { oldPassword: string; newPassword: string }): Promise<null> {
  return put<null>('/auth/password', data)
}
