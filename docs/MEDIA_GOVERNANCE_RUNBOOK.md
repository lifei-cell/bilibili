# 媒体处理与内容治理演示手册

## 闭环

1. 创作者上传：小文件保留 5 MB 分片与断点续传，20 MB 以上文件使用 15 分钟预签名 URL 直传 MinIO。
2. 媒体处理：RocketMQ 异步任务调用 FFmpeg/FFprobe，按源分辨率生成 360P、720P、1080P HLS，输出 Master Playlist，并在第 1 秒自动截取封面。
3. 分发：`CDN_BASE_URL` 作为播放地址前缀；Master、子清晰度 Playlist、TS 分片设置分层缓存策略。
4. 生命周期：临时上传默认 1 天清理，源视频默认 30 天清理；HLS 播放产物长期保留。
5. 内容治理：投稿先经过关键词、标签数量、发布频率风控，随后进入人工审核；高风险内容直接拒绝。
6. 管理员在 `/admin` 完成通过、拒绝、下架、恢复和举报处置，所有视频处置写入 `content_audit_log`。

## 演示

- 使用 `role=admin` 的账号登录，顶部出现“治理后台”。
- 创作者提交后页面显示“已提交审核”，视频不会提前出现在首页。
- 管理员通过视频后，首页、播放与搜索链路可见；播放页可切换 HLS 清晰度并提交举报。
- 举报成立会下架视频（或屏蔽评论/弹幕），驳回则保留内容。

## 生产配置

```env
MINIO_PUBLIC_ENDPOINT=https://object.example.com
MINIO_CORS_ALLOW_ORIGIN=https://video.example.com
CDN_BASE_URL=https://cdn.video.example.com
MINIO_TEMP_LIFECYCLE_DAYS=1
MINIO_SOURCE_LIFECYCLE_DAYS=30
```

生产环境应将 MinIO 放在私网源站，通过 CDN 回源；预签名域名必须与浏览器实际访问域名一致，否则 AWS SigV4 Host 签名会失效。

## 自动回归

```powershell
mvn -s .mvn/settings.xml verify
npm --prefix bilibili-web run quality
powershell -File scripts/e2e/reliability-e2e.ps1
```

Compose E2E 会验证上传、HLS Master、自动封面、人工审核发布、播放、搜索与互动，并清理测试数据和媒体目录。
