# Bilibili Cloud 项目亮点与后续优化方向

> 核对日期：2026-09-16。基线提交 `2d4767f`，另有未提交的发布验证、压测和转码配置改动；本文按当前工作区源码描述能力，历史测试结果按原报告口径引用，不代表本次重新验收或已发布。

## 已落地的项目亮点

1. **媒体处理闭环。** 视频服务支持 MD5 秒传、分片续传、MinIO 分片合并及直传；合并后写入持久化转码任务，由 RocketMQ 驱动 FFmpeg 生成多档 HLS，先发布可播放的低清晰度版本，再补充自适应版本。可展示上传失败恢复、转码状态查询、软硬件编码选择及降级逻辑。见 `UploadServiceImpl`、`VideoTranscodeTaskPublisher`、`VideoTranscodeConsumer`、`HlsTranscodeCommandBuilder`。
2. **可恢复的异步链路。** 通用 Outbox 保存事件，Inbox 用业务消息键与租约控制重复消费，有限重试后可进入应用 DLQ 并人工重放；转码另有专用任务表与超时重派。故障演练覆盖 MQ、Redis 和 Elasticsearch 恢复。语义是至少一次投递加消费幂等，允许重复消息，不能宣称 Exactly Once 或跨 MySQL、MinIO、MQ 强一致。见 `bilibili-common/.../reliability`、`docs/runbooks/reliability-closure.md`。
3. **实时弹幕与持久化解耦。** Netty WebSocket 按视频房间推送，Redis Pub/Sub 用于跨实例实时广播，RocketMQ 用于异步落库；一次性 Ticket、限流和请求幂等降低滥用与重复写入。Pub/Sub 不提供离线可靠投递。见 `bilibili-danmu-service`。
4. **读路径与可重建索引。** Redis 缓存和布隆过滤器降低热点读取与无效查询压力；Canal 订阅 MySQL Binlog，经消息链路维护缓存和 Elasticsearch。以 MySQL 为事实源，提供索引对账及全量重建。当前重建采用删除原索引后写回，仍有服务空窗。见 `CacheSyncConsumer`、`SearchIndexMaintenanceService`。
5. **工程化验证。** Java 21 / Spring Boot 4 多模块工程，包含 Flyway、Testcontainers、JaCoCo、前端 lint/Vitest/构建、Compose E2E、故障演练、读写压测、Prometheus/Grafana/日志追踪与 GitHub Actions。2026-09-11 本地候选验证记录：后端 72 个单元测试加 1 个集成测试、前端 4 个 Vitest、核心 E2E、故障演练和写链路 SLO 通过；资源门禁因视频服务 CPU 峰值 424.39% 超过当时 85% 阈值而失败。见 `docs/reports/2026-09-11-release-validation-blocked.md`。

## 证据边界

- 2026-08-25 本地 Docker、预热后列表/详情/搜索混合读链路持续 5 分钟达到 300.016 RPS、90,003 请求、错误率 0、整体 P95 12.35 ms。该结果不覆盖上传、转码、弹幕或生产环境；原始结果在被忽略的 `loadtest/results/`，仓库可见汇总见 `loadtest/PERFORMANCE_REPORT.md`。
- 2026-09-11 发布候选报告为 **未通过**；当时未以同一提交完成远端 CI 与交付闭环。当前脚本、配置和报告中仍有未提交改动，不能把其功能或测试记录表述为已发布版本。
- 前端现有测试主要覆盖格式工具和空状态组件，尚不足以证明上传、播放、弹幕、互动等业务旅程稳定。

## 优化顺序与验收

| 优先级 | 方向 | 具体动作与完成标准 |
| --- | --- | --- |
| P0 | 关闭发布门禁 | 固定工作区版本和测试环境，核对视频服务 4 CPU 配额下 `424.39%` 的采集口径、转码并发与历史任务；按容器配额归一化指标，必要时限制 FFmpeg/Worker 并发。完整复跑后要求报告 `passed=true`，并保留相同提交 SHA 的 CI 和构建产物。不要只调大阈值。 |
| P1 | 转码状态机正确性 | 为 Worker 增加数据库租约/代次或 fencing token、续租及带条件的完成写入；验证超时回收与旧 Worker 并发时只有当前持有者能更新任务和视频结果，并补覆盖崩溃、重复消息和降级成功的集成测试。当前 Redis 锁有 TTL，但任务完成更新未携带代次条件。 |
| P1 | 前端业务回归 | 补上传续传、发布播放、弹幕和互动的组件/页面测试，以及浏览器 E2E；将关键旅程并入 CI，失败时保留截图、日志和请求证据。 |
| P2 | 容量与编码矩阵 | 将读、写、转码负载分开测，记录 P95/P99、CPU 配额、内存、MQ 积压和任务完成时间；分别验证 CPU、Intel QSV、NVIDIA NVENC 与软件回退，明确各环境吞吐和成本边界。 |
| P3 | 部署与数据恢复 | 补 TLS/CDN、密钥管理、备份恢复演练；将 ES 全量重建改为影子索引加 Alias 切换，避免清空现有索引造成查询空窗。根据真实部署需求再评估 Helm/Kubernetes。 |
| P4 | 产品功能 | 在上述质量门禁稳定后，再按目标岗位和使用场景选择推荐、创作者中心、历史记录、审核体验等功能。 |

## 简历/答辩取材

优先讲三条：**分片上传到 HLS 播放**、**Outbox/Inbox/DLQ 故障恢复**、**WebSocket 弹幕实时广播与异步持久化**。每条按“业务问题 → 关键设计 → 失败窗口与恢复 → 验证证据 → 当前边界”展开。读链路 300 RPS 只作为有环境和范围限定的性能证据。
