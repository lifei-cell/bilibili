# 前端回归扩展记录（2026-09-17）

## 改动

- 小文件上传拆出 `uploadSmallVideo`，按服务端 `uploadedChunks` 跳过已完成分片；中断后重新调用 `/upload/check` 继续上传。单元测试覆盖跳过重复分片、中断重试和秒传；HTTP 拦截器测试覆盖并发 401 单次刷新、失败清理登录态与错误分支。
- 弹幕连接拆出 `DanmuConnection`，保留心跳、掉线重新取票并重连、页面离开时关闭和防止旧连接回调；单元测试覆盖重连与销毁。
- Playwright 启动真实 Vite 页面，以确定性 API/WebSocket 桩执行两条流程：登录 → 上传 → 发布 → 播放/清晰度切换 → 弹幕发送与重连收取；401 刷新令牌 → 分片网络失败 → 重新校验并续传。播放使用 2 秒 WebM 固定样本，断言 Chromium 的播放时间实际前进。
- `.github/workflows/frontend-browser.yml` 在 push/PR 自动运行 Chromium 测试；失败时上传截图、trace 和接口日志。`npm run quality` 的覆盖范围包含新业务服务。

## 本地验证

- `npm run quality`：通过；Vitest 14 项通过，覆盖率语句 95.89%、分支 87.77%、函数 96.87%，原有 90%/80% 门槛保持不变。
- `npm run test:e2e`：Chromium 2 项通过。

## 边界

浏览器测试使用网关桩，验证前端实际交互与恢复，不替代真实 MySQL、MinIO、RocketMQ、FFmpeg 的全链路验收。远端 CI 是否通过必须查看对应提交的 Actions 结果。
