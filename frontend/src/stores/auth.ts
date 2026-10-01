/**
 * 认证状态（Pinia）
 * token/refreshToken/user 持久化 localStorage（键 tgaws_auth，F1 约定）
 */
import { defineStore } from 'pinia'

import { loginApi, logoutApi, refreshApi } from '@/api/auth'
import type { LoginResult, UserInfo } from '@/types/api'

/** 本地持久化键 */
export const AUTH_STORAGE_KEY = 'tgaws_auth'

interface PersistedAuth {
  token: string
  refreshToken: string
  /** 秒 */
  expiresIn: number | null
  user: UserInfo | null
}

function loadPersisted(): PersistedAuth {
  const fallback: PersistedAuth = { token: '', refreshToken: '', expiresIn: null, user: null }
  try {
    const raw = localStorage.getItem(AUTH_STORAGE_KEY)
    if (!raw) return fallback
    const parsed = JSON.parse(raw) as Partial<PersistedAuth>
    return { ...fallback, ...parsed }
  } catch {
    return fallback
  }
}

export const useAuthStore = defineStore('auth', {
  state: (): PersistedAuth => loadPersisted(),

  getters: {
    isLoggedIn: (state): boolean => !!state.token,
    /** 权限点校验（4.7 权限点定义清单），供按钮级控制使用 */
    hasPermission: (state) => (perm: string): boolean => state.user?.permissions?.includes(perm) ?? false,
  },

  actions: {
    persist() {
      const snapshot: PersistedAuth = {
        token: this.token,
        refreshToken: this.refreshToken,
        expiresIn: this.expiresIn,
        user: this.user,
      }
      localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(snapshot))
    },

    /** 写入新 token 对（API-A03 刷新轮换后调用） */
    setTokens(token: string, refreshToken: string, expiresIn?: number) {
      this.token = token
      this.refreshToken = refreshToken
      if (expiresIn != null) this.expiresIn = expiresIn
      this.persist()
    },

    /** API-A01 登录 */
    async login(username: string, password: string): Promise<UserInfo> {
      const data: LoginResult = await loginApi({ username, password })
      this.token = data.token
      this.refreshToken = data.refreshToken
      this.expiresIn = data.expiresIn ?? null
      this.user = data.user ?? null
      this.persist()
      return data.user
    },

    /** API-A03 刷新令牌（轮换）；供 http.ts 401 流程调用 */
    async refresh(): Promise<void> {
      if (!this.refreshToken) {
        throw new Error('无可用 refreshToken')
      }
      const data = await refreshApi({ refreshToken: this.refreshToken })
      this.token = data.token
      this.refreshToken = data.refreshToken
      if (data.expiresIn != null) this.expiresIn = data.expiresIn
      this.persist()
    },

    /** API-A02 登出：通知后端令牌失效，并清空本地状态 */
    async logout(): Promise<void> {
      try {
        await logoutApi()
      } catch {
        // 后端不可达/令牌已失效时也照常清空本地状态
      } finally {
        this.clear()
      }
    },

    clear() {
      this.token = ''
      this.refreshToken = ''
      this.expiresIn = null
      this.user = null
      localStorage.removeItem(AUTH_STORAGE_KEY)
    },
  },
})
