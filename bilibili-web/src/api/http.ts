import axios from 'axios'
import type { AxiosRequestConfig, AxiosResponse } from 'axios'
import type { ApiResult, LoginResult } from '@/types/api'

export class ApiError extends Error {
  constructor(message: string, public status?: number) {
    super(message)
  }
}

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 20000,
  withCredentials: true,
})

const refreshClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10000,
  withCredentials: true,
})

let accessToken = ''
let refreshPromise: Promise<LoginResult> | null = null
let sessionListener: ((session: LoginResult | null) => void) | null = null

type RetryableConfig = AxiosRequestConfig & { _authRetry?: boolean }

export function setAccessToken(token: string) {
  accessToken = token
}

export function clearAccessToken() {
  accessToken = ''
}

export function observeAuthSession(listener: (session: LoginResult | null) => void) {
  sessionListener = listener
}

http.interceptors.request.use((config) => {
  if (accessToken) config.headers.satoken = accessToken
  return config
})

function needsRefresh(response: AxiosResponse<ApiResult<unknown>>) {
  return response.status === 401
    || (response.data?.success === false && response.data.errorMsg === '请先登录')
}

function canRefresh(config?: RetryableConfig) {
  return Boolean(config && !config._authRetry && !String(config.url).endsWith('/user/refresh'))
}

async function refreshSession() {
  if (!refreshPromise) {
    refreshPromise = refreshClient.post<ApiResult<LoginResult>>('/user/refresh')
      .then((response) => {
        if (!response.data.success || !response.data.data?.token) {
          throw new ApiError(response.data.errorMsg || '登录已过期', response.status)
        }
        accessToken = response.data.data.token
        sessionListener?.(response.data.data)
        return response.data.data
      })
      .catch((error) => {
        accessToken = ''
        sessionListener?.(null)
        throw error
      })
      .finally(() => { refreshPromise = null })
  }
  return refreshPromise
}

async function retryAfterRefresh(response: AxiosResponse<ApiResult<unknown>>) {
  const config = response.config as RetryableConfig
  config._authRetry = true
  await refreshSession()
  return http.request(config)
}

http.interceptors.response.use(
  async (response) => {
    const result = response.data as ApiResult<unknown>
    if (needsRefresh(response) && canRefresh(response.config as RetryableConfig)) {
      return retryAfterRefresh(response)
    }
    if (typeof result?.success === 'boolean' && !result.success) {
      return Promise.reject(new ApiError(result.errorMsg || '请求失败', response.status))
    }
    return response
  },
  async (error) => {
    if (error.response && needsRefresh(error.response)
      && canRefresh(error.config as RetryableConfig)) {
      return retryAfterRefresh(error.response)
    }
    const message = error.response?.data?.errorMsg || error.response?.data?.message || error.message || '网络连接失败'
    return Promise.reject(new ApiError(message, error.response?.status))
  },
)

export async function apiRequest<T>(config: Parameters<typeof http.request>[0]): Promise<ApiResult<T>> {
  const response = await http.request<ApiResult<T>>(config)
  return response.data
}

export default http
