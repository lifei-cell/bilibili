# Outbox 租约隔离与浏览器 CI（2026-11-02）

## 改动

- Outbox 使用每次领取独立的 token 和数据库租约期限；成功/失败回写都验证当前领取身份。V10 增量迁移保留历史已发布记录，旧处理中记录沿用超时回收规则。
- 新增真实 MySQL 并发、过期接管、重试上限、迁移兼容测试，以及发布器回写与指标测试。
- Push/PR CI 增加现有 Playwright 模拟网关回归；定时/手动发布验证增加真实浏览器链路，并将浏览器代码纳入 TypeScript 门禁。
- 真实链路通过页面完成登录、分片上传、转码等待、发布、播放与评论；审核使用独立管理员 API 会话，评论在刷新后重新读取。API、媒体和 WebSocket 均不做模拟。
- FFmpeg 为每次运行生成内容标识不同的短视频，避免秒传掩盖上传/转码问题。测试结束清理自身评论并软删除视频；测试存储仍会保留媒体、任务和软删除记录。

## 已验证

- `npm --prefix bilibili-web run quality`：ESLint、14 个单元测试、应用与浏览器代码类型检查、生产构建通过。
- `npm --prefix bilibili-web run test:e2e`：3 条模拟网关浏览器回归通过。
- PowerShell 两个修改脚本语法解析通过；真实浏览器用例的 Playwright 发现与类型检查通过。
- 干净构建中的用户 HTTP API 集成测试通过，确认全部 10 个数据库迁移成功应用。
- 最终版 Outbox 发布器 4 条单元测试、租约 MySQL 6 条集成测试通过；视频事务结果集成测试和视频模块覆盖率检查通过。
- 收尾复核落盘 Surefire/Failsafe 报告：各轮验证共 127 个测试，失败、错误、跳过均为 0；包含弹幕、社交、搜索和 Canal 单元测试。此统计不包含被 MinIO 拉取阻塞的用例，不代表全量发布验证通过。

## 验证边界

全量 `mvn -s .mvn/settings.xml clean verify` 运行到现有 `HlsAttemptCleanupIT` 时，MinIO 镜像 `quay.io/minio/minio:RELEASE.2024-12-13T22-19-12Z` 拉取持续返回 `401 UNAUTHORIZED`，因此停止了重复拉取。相同问题也阻止了真实浏览器环境启动；Docker Hub 同版本镜像尝试返回 pull access denied。未将这两项标记为通过，也未修改 CI 来忽略它们。

普通构建首次遇到旧 JaCoCo 数据不兼容，清理产物后用户服务的覆盖率检查通过。生产构建仍有原有的视频页 bundle 大小提示。

恢复镜像访问后，在隔离测试环境安装 Chromium，再执行 `pwsh scripts/verification/release-validation.ps1 -RunBrowserE2e`，完成全量后端和真实浏览器验收。部署租约变更前按 `docs/runbooks/outbox-lease-fencing.md` 停止旧发布实例后再升级。
