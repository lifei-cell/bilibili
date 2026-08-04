import axios from 'axios'
import type { ApiResult } from '@/types/api'

export class ApiError extends Error {
  constructor(message: string, public status?: number) {
    super(message)
  }
}

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 20000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('bili_token')
  if (token) config.headers.satoken = token
  return config
})

http.interceptors.response.use(
  (response) => {
    const result = response.data as ApiResult<unknown>
    if (typeof result?.success === 'boolean' && !result.success) {
      return Promise.reject(new ApiError(result.errorMsg || '请求失败', response.status))
    }
    return response
  },
  (error) => {
    const message = error.response?.data?.errorMsg || error.response?.data?.message || error.message || '网络连接失败'
    return Promise.reject(new ApiError(message, error.response?.status))
  },
)

export async function apiRequest<T>(config: Parameters<typeof http.request>[0]): Promise<ApiResult<T>> {
  const response = await http.request<ApiResult<T>>(config)
  return response.data
}

export default http
