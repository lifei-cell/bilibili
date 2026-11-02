import { expect, test } from '@playwright/test'
import type { APIResponse } from '@playwright/test'

async function result(response: APIResponse) {
  expect(response.ok(), `HTTP ${response.status()} ${response.url()}`).toBeTruthy()
  const body = await response.json()
  expect(body.success, body.errorMsg || response.url()).toBe(true)
  return body.data
}

// Uses only real HTTP, WebSocket and media responses. Run against an isolated demo stack.
test('real login → chunk upload → transcode → publish → approve → play → comment', async ({ page, request }, testInfo) => {
  const videoPath = process.env.E2E_VIDEO_PATH
  if (!videoPath) throw new Error('E2E_VIDEO_PATH must point to a fresh playable test video')
  const title = `browser-e2e-${Date.now()}`
  let videoId: number | undefined
  let creatorToken = ''
  let commentId: number | undefined
  const network: string[] = []
  page.on('response', response => {
    // Keep signed media URLs and tokens out of attached diagnostics.
    const path = new URL(response.url()).pathname
    if (path.startsWith('/api/')) network.push(`${response.status()} ${response.request().method()} ${path}`)
  })
  try {
    await page.goto('/upload')
    await expect(page).toHaveURL(/\/auth\?redirect=/)
    await page.getByPlaceholder('请输入用户名').fill(process.env.E2E_USERNAME || 'demo_alice')
    await page.getByPlaceholder('至少 8 位字符').fill(process.env.E2E_PASSWORD || 'Demo@123')
    const loginResponse = page.waitForResponse(response => response.url().endsWith('/api/user/login'))
    await page.locator('.submit-button').click()
    const login = await (await loginResponse).json()
    expect(login.success).toBe(true)
    creatorToken = login.data.token
    await expect(page).toHaveURL(/\/upload$/)

    await page.locator('input[type=file]').setInputFiles(videoPath)
    await page.getByPlaceholder('用一个好标题吸引观众').fill(title)
    await page.getByPlaceholder('多个标签用逗号分隔').fill('browser,e2e')
    const mergeResponse = page.waitForResponse(response => response.url().endsWith('/api/upload/merge'), { timeout: 60_000 })
    await page.getByRole('button', { name: '开始上传' }).click()
    const merged = await (await mergeResponse).json()
    expect(merged.success).toBe(true)
    expect(merged.data.transcodeTaskId).toBeTruthy()
    await expect(page.getByText('文件已就绪').first()).toBeVisible({ timeout: 180_000 })
    const publishedResponse = page.waitForResponse(response => response.url().endsWith('/api/video/publish'))
    await page.getByRole('button', { name: '提交作品' }).click()
    const published = await (await publishedResponse).json()
    expect(published.success).toBe(true)
    videoId = published.data.videoId
    expect(videoId).toBeTruthy()
    await expect(page.getByText('作品已提交审核', { exact: false })).toBeVisible()

    // Moderation is a separate actor; do not inject admin state into the creator browser.
    const admin = await result(await request.post('/api/user/login', { data: {
      username: process.env.E2E_ADMIN_USERNAME || 'demo_admin',
      password: process.env.E2E_ADMIN_PASSWORD || 'Demo@123', terminal: 'browser-e2e-admin',
    } }))
    await result(await request.post(`/api/admin/content/videos/${videoId}/audit`, {
      headers: { satoken: admin.token }, data: { action: 'APPROVE', remark: 'Browser E2E review' },
    }))

    await page.goto(`/video/${videoId}`)
    await expect(page.getByRole('heading', { name: title })).toBeVisible()
    await expect.poll(() => page.locator('video').evaluate(element => {
      const video = element as HTMLVideoElement
      return video.readyState >= 2 && video.videoWidth > 0 && video.currentTime > 0
    }), { timeout: 30_000 }).toBe(true)
    const comment = `真实浏览器评论-${Date.now()}`
    await page.getByPlaceholder('分享你的想法…').fill(comment)
    const commentResponse = page.waitForResponse(response => response.url().endsWith('/api/comment') && response.request().method() === 'POST')
    await page.locator('.comment-form').getByRole('button', { name: '发布' }).click()
    const created = await (await commentResponse).json()
    expect(created.success).toBe(true)
    commentId = created.data.commentId
    await expect(page.locator('.comment-list').getByText(comment, { exact: true })).toBeVisible()
    await page.reload()
    await expect(page.locator('.comment-list').getByText(comment, { exact: true })).toBeVisible()
  } finally {
    await testInfo.attach('api-log', { body: network.join('\n'), contentType: 'text/plain' })
    // The runner owns the isolated stack; remove public business data even on assertion failure.
    try {
      if (commentId) await result(await request.delete(`/api/comment/${commentId}`, { headers: { satoken: creatorToken } }))
    } finally {
      if (videoId) await result(await request.delete(`/api/video/${videoId}`, { headers: { satoken: creatorToken } }))
    }
  }
})
