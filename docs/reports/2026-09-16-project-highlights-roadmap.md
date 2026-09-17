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
| P0 | 关闭发布门禁 | CPU 采集已按容器配额归一化，并分别记录视频服务与运行中的转码 Worker；待 Docker Engine 可访问后，在固定工作区版本上完整复跑，要求报告 `passed=true`，并保留相同提交 SHA 的 CI 和构建产物。不要只调大阈值。 |
| P1 | 转码状态机正确性 | 为 Worker 增加数据库租约/代次或 fencing token、续租及带条件的完成写入；验证超时回收与旧 Worker 并发时只有当前持有者能更新任务和视频结果，并补覆盖崩溃、重复消息和降级成功的集成测试。当前 Redis 锁有 TTL，但任务完成更新未携带代次条件。 |
| P1 | 前端业务回归 | 补上传续传、发布播放、弹幕和互动的组件/页面测试，以及浏览器 E2E；将关键旅程并入 CI，失败时保留截图、日志和请求证据。 |
| P2 | 容量与编码矩阵 | 将读、写、转码负载分开测，记录 P95/P99、CPU 配额、内存、MQ 积压和任务完成时间；分别验证 CPU、Intel QSV、NVIDIA NVENC 与软件回退，明确各环境吞吐和成本边界。 |
| P3 | 部署与数据恢复 | 补 TLS/CDN、密钥管理、备份恢复演练；将 ES 全量重建改为影子索引加 Alias 切换，避免清空现有索引造成查询空窗。根据真实部署需求再评估 Helm/Kubernetes。 |
| P4 | 产品功能 | 在上述质量门禁稳定后，再按目标岗位和使用场景选择推荐、创作者中心、历史记录、审核体验等功能。 |

## 可执行任务清单

### 1. 校正资源门禁并复跑发布验证（P0）

- **现状：** `docker-compose.service.yml` 给视频服务和可选转码 Worker 各配置了 `4.00` CPU。历史报告的 424.39% 是 Docker 原始 CPU 百分比，不能直接解释为单核资源超限。
- **改动：** `scripts/loadtest/resource-normalization.ps1` 读取容器 CPU 配额，用 `raw / quotaCores` 计算配额使用率；不能读取配额时门禁失败。写链路分别采集视频服务和运行中的专用 Worker，并在报告中保留原始值、配额核数、归一化峰值和角色汇总。1 核、4 核样本校验已通过。FFmpeg 子进程、任务队列深度和每档转码耗时仍是后续观测项。
- **验收：** 代码级归一化校验通过；完整发布复跑受 Docker 命名管道权限阻塞，最新报告为 `passed=false`，恢复 Docker 后仍需在固定版本上完成 E2E、Worker 采样、资源与 MQ 门禁并取得 `passed=true`，再保存同一提交 SHA 的远端 CI 产物。

### 2. 修复转码完成时的双写窗口（P0）

**状态：** 已于 2026-09-17 完成事务化修复与故障注入验证，记录见 `docs/reports/2026-09-17-transcode-result-transaction.md`。

- **现状：** `VideoTranscodeConsumer.publishResult` 先调用 `VideoTranscodeTaskMapper.markSuccess`，再调用 `VideoMapper.updateMediaByFileMd5`，中间没有事务。如果第二步抛错，任务可能已是成功状态，后续消息在入口直接跳过，视频播放地址无法靠重试修复。
- **改动：** 将两次 MySQL 更新移入独立 Spring Service 的事务方法，由消费端调用；先验证任务当前代次，再一次事务提交任务状态与所有关联视频的播放地址。MinIO 产物与数据库仍是最终一致，需要保留失败后对账修复入口。保留低清晰度先发布的阶段语义，不让后续档位失败清空已可播放结果。
- **验收：** 注入第二次数据库写入异常，验证任务状态回滚且可重试；重复消息和后续档位失败时，视频仍可播放且结果不倒退。

### 3. 给转码 Worker 增加代次保护（P1）

**状态：** 数据库租约、代次写入条件、心跳续租和独立 HLS 产物前缀已实现；旧 Worker 超时后的数据库竞争与对象隔离测试通过。记录见 `docs/reports/2026-09-17-transcode-lease-fencing.md`。真实容器双 Worker 演练仍受 Docker Engine 环境阻塞。

- **机制：** Flyway V6 增加 `claim_generation`、`claim_token` 和 `lease_until`。领取、续租、失败和完成均由数据库条件更新保护；低清晰度与最终结果写库仍保持同一事务。每次尝试使用独立 MinIO 产物前缀。
- **后续验收：** 恢复 Docker 后运行真实 MySQL、RocketMQ、MinIO、FFmpeg 双 Worker 演练，覆盖进程崩溃、MQ 重投、旧 Worker 迟到及租约到期回收；补孤立尝试对象清理与高档位超时补偿。

### 4. 扩大前端回归范围（P1）

- **现状更新（2026-09-17）：** 分片续传和弹幕连接逻辑已抽出并加入单元测试；Playwright 浏览器测试覆盖登录、上传、发布、播放、弹幕，以及 401 刷新和分片中断恢复。独立 CI 工作流在 push/PR 自动执行，见 `docs/reports/2026-09-17-frontend-regression.md`。本地 Chromium 测试通过，远端 CI 结果需以对应提交运行记录为准。
- **改动：** 先抽离上传状态机/API 与弹幕连接逻辑，分别测试断点续传、重复分片、401 刷新和 WebSocket 重连；再用浏览器 E2E 覆盖“登录 → 上传 → 发布 → 播放 → 弹幕/互动”及失败恢复。将关键旅程接入 CI，并保存失败截图和接口日志。
- **验收：** 核心路径及至少一个中断恢复场景自动通过，覆盖率指标包含上述业务模块；不以工具组件的高覆盖率替代页面质量。

### 5. 索引无空窗重建与生产运维（P2）

- **现状更新（2026-09-17）：** 已实现 MySQL 游标分页、影子索引、对账后 Alias 原子切换和保留旧索引回退。单实例 CDC 写入在最终追平时暂缓。多实例协调、规模化压测和容器端到端验收仍待完成，见 `docs/reports/2026-09-17-es-shadow-rebuild.md`。
- **改动：** 按 ID 分页读取 MySQL，写入带版本号的新索引；追平增量后对账，使用 ES Alias 原子切换，保留旧索引至回滚窗口结束。另做备份恢复、TLS/CDN 和密钥注入演练。
- **验收：** 重建过程中查询仍返回旧索引结果；切换后对账一致，失败可切回旧索引；用规模化数据验证内存上限与耗时。

## 简历/答辩取材

优先讲三条：**分片上传到 HLS 播放**、**Outbox/Inbox/DLQ 故障恢复**、**WebSocket 弹幕实时广播与异步持久化**。每条按“业务问题 → 关键设计 → 失败窗口与恢复 → 验证证据 → 当前边界”展开。读链路 300 RPS 只作为有环境和范围限定的性能证据。
