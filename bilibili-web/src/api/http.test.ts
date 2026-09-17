import type { AxiosAdapter, AxiosRequestConfig, AxiosResponse } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import http, { apiRequest, clearAccessToken, observeAuthSession, refreshClient, setAccessToken } from './http'

const ok = (data: unknown) => ({ success: true, errorMsg: null, data, total: null })
const expired = { success: false, errorMsg: '请先登录', data: null, total: null }
const session = { token: 'new-token', expiresIn: 3600, user: { id: 1, username: 'creator', nickname: 'Creator', avatar: '', role: 'user' } }

function response(config: AxiosRequestConfig, status: number, data: unknown): AxiosResponse {
  return { config: config as AxiosResponse['config'], status, statusText: String(status), headers: {}, data }
}

describe('session refresh interceptor', () => {
  const originalAdapter = http.defaults.adapter
  const originalRefreshAdapter = refreshClient.defaults.adapter

  beforeEach(() => { clearAccessToken(); observeAuthSession(() => {}) })
  afterEach(() => {
    http.defaults.adapter = originalAdapter
    refreshClient.defaults.adapter = originalRefreshAdapter
    clearAccessToken()
    observeAuthSession(() => {})
  })

  it('retries concurrent 401 requests with one refreshed token', async () => {
    const sessions = vi.fn()
    observeAuthSession(sessions)
    setAccessToken('expired-token')
    const refresh = vi.fn(async (config: AxiosRequestConfig) => response(config, 200, ok(session)))
    refreshClient.defaults.adapter = refresh as AxiosAdapter
    const requests: string[] = []
    http.defaults.adapter = (async config => {
      requests.push(String(config.headers?.satoken))
      return response(config, config.headers?.satoken === 'new-token' ? 200 : 401,
        config.headers?.satoken === 'new-token' ? ok({ value: 1 }) : expired)
    }) as AxiosAdapter

    const [first, second] = await Promise.all([
      apiRequest<{ value: number }>({ method: 'GET', url: '/one' }),
      apiRequest<{ value: number }>({ method: 'GET', url: '/two' }),
    ])

    expect(first.data.value).toBe(1)
    expect(second.data.value).toBe(1)
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(requests).toEqual(['expired-token', 'expired-token', 'new-token', 'new-token'])
    expect(sessions).toHaveBeenCalledWith(session)
  })

  it('clears the session when refresh fails', async () => {
    const sessions = vi.fn()
    observeAuthSession(sessions)
    setAccessToken('expired-token')
    refreshClient.defaults.adapter = (async config => response(config, 200, expired)) as AxiosAdapter
    http.defaults.adapter = (async config => response(config, 401, expired)) as AxiosAdapter

    await expect(apiRequest({ method: 'GET', url: '/private' })).rejects.toThrow('请先登录')
    expect(sessions).toHaveBeenCalledWith(null)
  })

  it('does not recursively refresh the refresh endpoint', async () => {
    const refresh = vi.fn()
    refreshClient.defaults.adapter = refresh as AxiosAdapter
    http.defaults.adapter = (async config => response(config, 401, expired)) as AxiosAdapter

    await expect(apiRequest({ method: 'POST', url: '/user/refresh' })).rejects.toThrow('请先登录')
    expect(refresh).not.toHaveBeenCalled()
  })

  it('reports business and network failures without changing the session', async () => {
    const sessions = vi.fn()
    observeAuthSession(sessions)
    http.defaults.adapter = (async config => response(config, 200, { success: false, errorMsg: '业务拒绝', data: null })) as AxiosAdapter
    await expect(apiRequest({ method: 'GET', url: '/business' })).rejects.toThrow('业务拒绝')

    http.defaults.adapter = (async () => { throw new Error('connection reset') }) as AxiosAdapter
    await expect(apiRequest({ method: 'GET', url: '/network' })).rejects.toThrow('connection reset')
    expect(sessions).not.toHaveBeenCalled()
  })

  it('retries a rejected 401 response and surfaces non-auth server errors', async () => {
    setAccessToken('expired-token')
    refreshClient.defaults.adapter = (async config => response(config, 200, ok(session))) as AxiosAdapter
    http.defaults.adapter = (async config => {
      if (config.headers?.satoken !== 'new-token') {
        throw { config, response: response(config, 401, expired) }
      }
      return response(config, 200, ok({ recovered: true }))
    }) as AxiosAdapter
    await expect(apiRequest({ method: 'GET', url: '/private' })).resolves.toEqual(ok({ recovered: true }))

    http.defaults.adapter = (async config => {
      throw { config, response: response(config, 503, { errorMsg: '服务不可用' }) }
    }) as AxiosAdapter
    await expect(apiRequest({ method: 'GET', url: '/server' })).rejects.toThrow('服务不可用')
  })
})
