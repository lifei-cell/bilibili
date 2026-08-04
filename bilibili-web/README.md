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

生产环境可复制 `.env.example` 为 `.env.production`，通过 `VITE_API_BASE_URL` 指定 API 地址。若前端与网关同域部署，保持 `/api` 即可。

## 已接入功能

- 视频推荐、分区和排序
- 搜索、热搜和搜索建议接口封装
- 视频详情、播放清晰度、弹幕列表与发送
- 点赞、关注、收藏、评论
- 用户登录、短信登录、注册、退出和个人资料
- MD5 秒传、5MB 分片上传、合并与视频发布
- 创作者个人空间和公开视频列表

登录态令牌按照后端约定存储在本地，并自动通过 `satoken` 请求头发送。所有 API 都按照统一的 `{ success, errorMsg, data, total }` 响应结构处理。
