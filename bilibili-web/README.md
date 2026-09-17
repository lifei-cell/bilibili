# BiliCloud Web

基于 Vue 3、TypeScript、Vite、Pinia 和 Vue Router 的视频社区前端，接口对接当前仓库的 Bilibili Cloud 网关。

## 启动

先确保后端网关运行在 `http://localhost:8080`，然后执行：

```bash
npm install
npm run dev
```

访问 `http://localhost:5173`。开发服务器会把 `/api` 请求和 WebSocket 请求代理到后端网关。

## 构建

```bash
npm run build
```

## 回归测试

```bash
npm run quality
npx playwright install chromium
npm run test:e2e
```

Vitest 覆盖分片续传与弹幕 WebSocket 重连；Playwright 使用本地 Vite 页面和确定性网关桩，覆盖登录、上传、发布、播放、弹幕与 401 刷新、分片中断恢复。浏览器测试不依赖已启动的后端。CI 在 push 和 PR 时运行，并在失败时上传截图、trace 和接口日志。

生产环境可复制 `.env.example` 为 `.env.production`，通过 `VITE_API_BASE_URL` 指定 API 地址。若前端与网关同域部署，保持 `/api` 即可。

## 已接入功能

- 视频推荐、分区和排序
- 搜索、热搜和搜索建议接口封装
- 视频详情、播放清晰度、弹幕列表、实时 WebSocket 弹幕与发送
- 从后端分类接口加载分区，保留离线 fallback
- 点赞、关注、收藏、评论
- 用户登录、短信登录、注册、退出和个人资料
- MD5 秒传、5MB 分片上传、合并与视频发布
- 创作者个人空间和公开视频列表

登录态令牌按照后端约定存储在本地，并自动通过 `satoken` 请求头发送。所有 API 都按照统一的 `{ success, errorMsg, data, total }` 响应结构处理。
