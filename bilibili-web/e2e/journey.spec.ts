import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'
import { readFile } from 'node:fs/promises'

const user = { id: 11, username: 'creator', nickname: 'Creator', avatar: '', role: 'user' }
const ok = (data: unknown) => ({ success: true, errorMsg: null, data, total: null })
const apiLogs = new WeakMap<Page, string[]>()

test.beforeEach(async ({ page }) => {
  const logs: string[] = []
  apiLogs.set(page, logs)
  page.on('response', response => {
    const path = new URL(response.url()).pathname
    if (path.startsWith('/api/')) logs.push(`${response.status()} ${response.request().method()} ${path}`)
  })
  await page.addInitScript(() => {
    const scope = window as unknown as { socketUrls: string[]; WebSocket: typeof WebSocket }
    scope.socketUrls = []
    const NativeWebSocket = window.WebSocket
    class FakeSocket {
      static OPEN = 1
      readyState = 1
      onopen: (() => void) | null = null
      onclose: (() => void) | null = null
      onerror: (() => void) | null = null
      onmessage: ((event: { data: string }) => void) | null = null
      constructor(url: string) {
        scope.socketUrls.push(url)
        const sequence = scope.socketUrls.length
        setTimeout(() => {
          this.onopen?.()
          if (sequence === 1) setTimeout(() => this.onclose?.(), 100)
          if (sequence === 2) setTimeout(() => this.onmessage?.({
            data: JSON.stringify({ type: 'danmu', data: { id: 88, userId: 12, content: '重连成功', color: '#FFFFFF', position: 0, fontSize: 25, videoTime: 0, sendTime: new Date().toISOString() } }),
          }), 100)
        }, 0)
      }
      send() {}
      close() {}
    }
    scope.WebSocket = new Proxy(NativeWebSocket, {
      construct(target, args) {
        if (String(args[0]).includes('/api/danmu/ws/')) return new FakeSocket(String(args[0])) as unknown as WebSocket
        return new target(...args)
      },
    })
  })
})

test.afterEach(async ({ page }, testInfo) => {
  if (testInfo.status !== testInfo.expectedStatus) {
    await testInfo.attach('api-log', { body: (apiLogs.get(page) || []).join('\n'), contentType: 'text/plain' })
  }
})

async function mockGateway(page: Page, recovery = false) {
  const playableVideo = await readFile(new URL('./fixtures/black-2s.webm', import.meta.url))
  let loggedIn = false
  let refreshCount = 0
  let checkCount = 0
  let chunkCount = 0
  const checkTokens: string[] = []
  await page.route(/^https?:\/\/[^/]+\/api\//, async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const method = request.method()
    const fulfill = (data: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(ok(data)) })
    if (path === '/api/user/refresh') {
      refreshCount += 1
      return loggedIn ? fulfill({ token: 'token-2', expiresIn: 3600, user })
        : route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ success: false, errorMsg: '请先登录', data: null, total: null }) })
    }
    if (path === '/api/user/login') { loggedIn = true; return fulfill({ token: 'token-1', expiresIn: 3600, user }) }
    if (path === '/api/user/me') return fulfill(user)
    if (path === '/api/category/list') return fulfill([{ id: 8, parentId: 0, name: '科技', sort: 1 }])
    if (path === '/api/upload/check') {
      checkCount += 1
      checkTokens.push(request.headers().satoken || '')
      if (recovery && checkCount === 1) return route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ success: false, errorMsg: '请先登录', data: null, total: null }) })
      return fulfill({ instant: false, videoId: null, sourceUrl: null, uploadId: 'upload-1', uploadedChunks: recovery && checkCount >= 3 ? [0] : [] })
    }
    if (path === '/api/upload/chunk') {
      chunkCount += 1
      if (recovery && chunkCount === 2) return route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ success: false, errorMsg: '网络中断', data: null, total: null }) })
      return fulfill({ chunkIndex: chunkCount - 1, uploaded: true })
    }
    if (path === '/api/upload/merge') return fulfill({ sourceUrl: 'upload/source.mp4', fileMd5: 'md5', transcodeTaskId: 'task-1', transcodeStatus: 'completed' })
    if (path === '/api/upload/transcode/task-1') return fulfill({ taskId: 'task-1', status: 'completed', retryCount: 0, errorMessage: null, outputUrl: '/media/77.m3u8', coverUrl: null, nextRetryTime: null })
    if (path === '/api/video/publish') return fulfill({ videoId: 77, status: 0 })
    if (path === '/api/video/77') return fulfill({
      id: 77, title: '测试作品', description: '浏览器流程', coverUrl: '', duration: 2, categoryId: 8, tags: ['test'],
      author: { id: 11, nickname: 'Creator', avatar: '' },
      stats: { viewCount: 1, likeCount: 0, collectCount: 0, danmuCount: 0, commentCount: 0 },
    })
    if (path === '/api/video/77/play') return fulfill({ videoId: 77, defaultQuality: '480P', qualities: [
      { quality: '480P', url: '/media/480.webm' }, { quality: '720P', url: '/media/720.webm' },
    ], playToken: 'play-token' })
    if (path === '/api/comment/list/77' || path === '/api/danmu/list/77') return fulfill([])
    if (path === '/api/like/status') return fulfill({ liked: false })
    if (path === '/api/follow/status/11') return fulfill({ isFollowing: false })
    if (path === '/api/danmu/ws-ticket/77') return fulfill({ ticket: 'ws-ticket', expiresIn: 60 })
    if (path === '/api/danmu/send') return fulfill({ danmuId: 99 })
    if (path === '/api/video/list') return fulfill([])
    throw new Error(`Unexpected API request: ${method} ${path}`)
  })
  await page.route('**/media/**', route => route.fulfill({ status: 200, contentType: 'video/webm', body: playableVideo }))
  return { get refreshCount() { return refreshCount }, get checkCount() { return checkCount }, get chunkCount() { return chunkCount }, checkTokens }
}

async function loginAndChooseVideo(page: Page) {
  await page.goto('/upload')
  await expect(page).toHaveURL(/\/auth\?redirect=/)
  await page.getByPlaceholder('请输入用户名').fill('creator')
  await page.getByPlaceholder('至少 8 位字符').fill('Password123')
  await page.locator('.submit-button').click()
  await expect(page).toHaveURL(/\/upload$/)
  await page.locator('input[type=file]').setInputFiles({
    name: 'journey.mp4', mimeType: 'video/mp4', buffer: Buffer.alloc(5 * 1024 * 1024 + 16, 1),
  })
}

test('login, upload, publish, play and receive danmu after reconnect', async ({ page }) => {
  const gateway = await mockGateway(page)
  await loginAndChooseVideo(page)
  await page.getByRole('button', { name: '开始上传' }).click()
  await expect(page.getByText('文件已就绪').first()).toBeVisible()
  expect(gateway.chunkCount).toBe(2)
  await page.getByPlaceholder('多个标签用逗号分隔').fill('test')
  await page.getByRole('button', { name: '提交作品' }).click()
  await expect(page.getByText('作品已提交审核')).toBeVisible()

  await page.goto('/video/77')
  await expect(page.getByRole('heading', { name: '测试作品' })).toBeVisible()
  await expect(page.locator('video')).toHaveAttribute('src', '/media/480.webm')
  await expect.poll(() => page.locator('video').evaluate(element => (element as HTMLVideoElement).currentTime)).toBeGreaterThan(0)
  await page.locator('.player-bar select').selectOption('720P')
  await expect(page.locator('video')).toHaveAttribute('src', '/media/720.webm')
  await expect.poll(() => page.evaluate(() => (window as unknown as { socketUrls: string[] }).socketUrls.length)).toBe(2)
  await expect(page.getByText('重连成功').first()).toBeVisible()
  await page.getByPlaceholder('发一条友善的弹幕吧').fill('我的弹幕')
  await page.locator('.player-bar button').click()
  await expect(page.getByText('我的弹幕').first()).toBeVisible()
})

test('401 refresh and interrupted chunk upload recover through the UI', async ({ page }) => {
  const gateway = await mockGateway(page, true)
  await loginAndChooseVideo(page)
  await page.getByRole('button', { name: '开始上传' }).click()
  await expect(page.getByText('网络中断')).toBeVisible()
  await page.getByRole('button', { name: '开始上传' }).click()
  await expect(page.getByText('文件已就绪').first()).toBeVisible()
  expect(gateway.refreshCount).toBeGreaterThanOrEqual(2)
  expect(gateway.checkCount).toBe(3)
  expect(gateway.checkTokens).toEqual(['token-1', 'token-2', 'token-2'])
  expect(gateway.chunkCount).toBe(3)
})
