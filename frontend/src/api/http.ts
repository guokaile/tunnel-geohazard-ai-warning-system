/**
 * axios 实例与全局拦截器
 *
 * 对齐《4.接口设计说明书》：
 * - 4.2 统一响应 {"code":"00000","message":"success","data":{...},"timestamp":...}：拦截器统一解包，
 *   调用方直接拿 data；业务码非 00000 统一 ElMessage 报错并 reject
 * - 4.2 鉴权：Authorization: Bearer {token}；access 30min + refresh 2h 滑动轮换
 * - 4.2 防重放：X-Ts（毫秒时间戳）+ X-Nonce（随机串）
 * - 4.2 幂等：创建/修改/删除类接口请求头 Idempotency-Key（UUID）
 * - HTTP 401（或业务码 C0006/C0007）→ 自动 refresh 轮换并重放原请求一次；仍失败则清状态跳登录页
 */
import axios, {
  AxiosError,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { ElMessage } from 'element-plus'

import router from '@/router'
import { useAuthStore } from '@/stores/auth'
import type { Result } from '@/types/api'

/** 业务成功码（4.2 统一响应） */
export const SUCCESS_CODE = '00000'

/** 业务异常：携带后端错误码与 message（前端按 code 映射提示，4.2 国际化约定） */
export class ApiError extends Error {
  readonly code: string

  constructor(code: string, message: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

export interface RequestConfig extends AxiosRequestConfig {
  /** 为 true 时业务/HTTP 错误不弹全局 ElMessage，由调用方自行提示（如登录页取 message 展示） */
  skipErrorMessage?: boolean
  /** 内部标记：401 刷新后已重试一次，防止递归 */
  _retried?: boolean
}

/** 带全局拦截器的主实例：baseURL /api/v1（dev 经 vite 代理 → http://localhost:8080） */
const http = axios.create({ baseURL: '/api/v1', timeout: 15000 })

/**
 * 无业务拦截器的裸实例：仅用于刷新令牌（API-A03），避免刷新请求自身进入 401 重试递归。
 * 仅附加 4.2 防重放头。
 */
export const rawHttp = axios.create({ baseURL: '/api/v1', timeout: 15000 })

rawHttp.interceptors.request.use((config) => {
  config.headers.set('X-Ts', String(Date.now()))
  config.headers.set('X-Nonce', crypto.randomUUID())
  return config
})

/* ---------------- 请求拦截：鉴权 + 防重放 + 幂等（4.2） ---------------- */
http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token && !config.headers.Authorization) {
    config.headers.set('Authorization', `Bearer ${auth.token}`)
  }
  config.headers.set('X-Ts', String(Date.now()))
  config.headers.set('X-Nonce', crypto.randomUUID())
  const method = (config.method ?? 'get').toLowerCase()
  if ((method === 'post' || method === 'put' || method === 'delete') && !config.headers.has('Idempotency-Key')) {
    // 401 重放复用同一 config，幂等键保持不变，后端 24h 窗口同键返回首次结果
    config.headers.set('Idempotency-Key', crypto.randomUUID())
  }
  return config
})

/* ---------------- 401 单飞刷新 ---------------- */
let refreshPromise: Promise<boolean> | null = null

/** 并发多个 401 只触发一次 refresh（单飞），返回是否刷新成功 */
function tryRefreshToken(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = useAuthStore()
      .refresh()
      .then(() => true)
      .catch(() => false)
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

function redirectToLogin() {
  const current = router.currentRoute.value
  if (current.path !== '/login') {
    router.replace({ path: '/login', query: { redirect: current.fullPath } }).catch(() => {})
  }
}

/** 刷新成功后携带新 token 重放原请求；失败则清状态并跳登录页 */
async function retryWithNewToken(
  config: InternalAxiosRequestConfig & RequestConfig,
  bizBody?: Result,
): Promise<unknown> {
  const ok = await tryRefreshToken()
  if (ok) {
    config._retried = true
    config.headers.set('Authorization', `Bearer ${useAuthStore().token}`)
    return http.request(config) // 重放原请求，响应再次经本拦截器统一解包
  }
  const auth = useAuthStore()
  auth.clear()
  const message = bizBody?.message || '登录状态已失效，请重新登录'
  if (!config.skipErrorMessage) {
    ElMessage.error(message)
  }
  redirectToLogin()
  return Promise.reject(new ApiError(bizBody?.code ?? 'C0006', message))
}

/* ---------------- 响应拦截：统一解包 + 业务码校验 ---------------- */
http.interceptors.response.use(
  // 注意：拦截器实际返回的是解包后的 data（调用方直接拿业务数据），与 axios 声明的 AxiosResponse 不符，故标注 any
  (response: AxiosResponse): any => {
    const body = response.data as Result
    // 非标准响应（文件流等）原样返回
    if (!body || typeof body !== 'object' || typeof body.code !== 'string') {
      return body
    }
    if (body.code === SUCCESS_CODE) {
      return body.data
    }
    const config = response.config as InternalAxiosRequestConfig & RequestConfig
    // 令牌无效/过期（4.6：C0006 未登录/令牌无效、C0007 令牌已过期）→ 刷新后重试一次
    if ((body.code === 'C0006' || body.code === 'C0007') && !config._retried) {
      return retryWithNewToken(config, body)
    }
    if (!config.skipErrorMessage) {
      ElMessage.error(body.message || '请求失败')
    }
    return Promise.reject(new ApiError(body.code, body.message || '请求失败'))
  },
  (error: AxiosError): Promise<never> => {
    const config = error.config as (InternalAxiosRequestConfig & RequestConfig) | undefined
    const status = error.response?.status
    // HTTP 401（4.2：401 未认证/令牌无效）→ 刷新后重试一次
    if (status === 401 && config && !config._retried) {
      return retryWithNewToken(config) as Promise<never>
    }
    const serverMessage = (error.response?.data as Partial<Result> | undefined)?.message
    const message = serverMessage || (error.response ? `请求失败（HTTP ${status}）` : '网络异常，请检查网络连接')
    if (config && !config.skipErrorMessage) {
      ElMessage.error(message)
    }
    return Promise.reject(new ApiError(String(status ?? 'NETWORK'), message))
  },
)

/* ---------------- 类型化请求助手（返回已解包的 data） ---------------- */
export function request<T>(config: RequestConfig): Promise<T> {
  return http.request(config) as unknown as Promise<T>
}

export function get<T>(url: string, params?: object, config?: RequestConfig): Promise<T> {
  return request<T>({ url, method: 'get', params, ...config })
}

export function post<T>(url: string, data?: unknown, config?: RequestConfig): Promise<T> {
  return request<T>({ url, method: 'post', data, ...config })
}

export function put<T>(url: string, data?: unknown, config?: RequestConfig): Promise<T> {
  return request<T>({ url, method: 'put', data, ...config })
}

export function del<T>(url: string, config?: RequestConfig): Promise<T> {
  return request<T>({ url, method: 'delete', ...config })
}

/** 文件下载：响应为二进制流，拦截器对非 JSON 原样透传，返回完整 AxiosResponse（取 data/data.headers） */
export function downloadBlob(
  url: string,
  params?: object,
  config?: RequestConfig,
): Promise<import('axios').AxiosResponse<Blob>> {
  return http.request({ url, method: 'get', params, responseType: 'blob', ...config })
}
