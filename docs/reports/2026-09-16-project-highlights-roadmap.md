# Bilibili Cloud 项目亮点与后续优化方向

> 核对日期：2026-09-18。本文按当前工作区源码和对应复验报告描述能力；本地 Docker 证据、远端 CI 和生产状态分开记录，不把未提交改动表述为已发布版本。

## 已落地的项目亮点

1. **媒体处理闭环。** 视频服务支持 MD5 秒传、分片续传、MinIO 分片合并及直传；合并后写入持久化转码任务，由 RocketMQ 驱动 FFmpeg 生成多档 HLS，先发布可播放的低清晰度版本，再补充自适应版本。可展示上传失败恢复、转码状态查询、软硬件编码选择及降级逻辑。见 `UploadServiceImpl`、`VideoTranscodeTaskPublisher`、`VideoTranscodeConsumer`、`HlsTranscodeCommandBuilder`。
2. **可恢复的异步链路。** 通用 Outbox 保存事件，Inbox 用业务消息键与租约控制重复消费，有限重试后可进入应用 DLQ 并人工重放；转码另有专用任务表与超时重派。故障演练覆盖 MQ、Redis 和 Elasticsearch 恢复。语义是至少一次投递加消费幂等，允许重复消息，不能宣称 Exactly Once 或跨 MySQL、MinIO、MQ 强一致。见 `bilibili-common/.../reliability`、`docs/runbooks/reliability-closure.md`。
3. **实时弹幕与持久化解耦。** Netty WebSocket 按视频房间推送，Redis Pub/Sub 用于跨实例实时广播，RocketMQ 用于异步落库；一次性 Ticket、限流和请求幂等降低滥用与重复写入。Pub/Sub 不提供离线可靠投递。见 `bilibili-danmu-service`。
4. **读路径与可重建索引。** Redis 缓存和布隆过滤器降低热点读取与无效查询压力；Canal 订阅 MySQL Binlog，经消息链路维护缓存和 Elasticsearch。以 MySQL 为事实源，提供影子索引、对账和 Alias 原子切换；重建及视频 CDC 写入由 MySQL 命名锁协调多 Canal 实例。见 `CacheSyncConsumer`、`SearchIndexMaintenanceService`。
5. **工程化验证。** Java 21 / Spring Boot 4 多模块工程，包含 Flyway、Testcontainers、JaCoCo、前端 lint/Vitest/构建、Compose E2E、故障演练、读写压测、Prometheus/Grafana/日志追踪与 GitHub Actions。2026-09-17 本地 Docker 复验已通过发布门禁，并补通过双 Worker 崩溃接管和双 Canal 索引切换；2026-09-18 四场景容量基线也已通过。证据见 `docs/reports/2026-09-17-release-validation-pass.md`、`docs/reports/2026-09-17-p1-reliability-drill.md` 和 `docs/reports/2026-09-18-p2-capacity-baseline.md`。

## 证据边界

- 2026-08-25 本地 Docker、预热后列表/详情/搜索混合读链路持续 5 分钟达到 300.016 RPS、90,003 请求、错误率 0、整体 P95 12.35 ms。该结果不覆盖上传、转码、弹幕或生产环境；原始结果在被忽略的 `loadtest/results/`，仓库可见汇总见 `loadtest/PERFORMANCE_REPORT.md`。
- 2026-09-11 发布候选报告为 **未通过**；当时未以同一提交完成远端 CI 与交付闭环。当前脚本、配置和报告中仍有未提交改动，不能把其功能或测试记录表述为已发布版本。
- 2026-09-18 P2 本地容量报告中，读 50 iterations/s（约 150 HTTP RPS）、写 1 operation/s、弹幕 1 message/s、转码 1 task/s（2 Worker）均通过；该结果仍只代表当前 Docker Desktop 和固定数据集。
- 前端现有测试主要覆盖格式工具和空状态组件，尚不足以证明上传、播放、弹幕、互动等业务旅程稳定。

## 优化顺序与验收

| 优先级 | 方向 | 具体动作与完成标准 |
| --- | --- | --- |
| P0 | 关闭发布门禁 | **已完成（2026-09-17）**。CPU 采集按容器配额归一化；Docker Engine 恢复后真实 Compose E2E、故障演练、写链路 SLO、MQ 积压和资源门禁均通过，最终报告见 `docs/reports/2026-09-17-release-validation-pass.md`。 |
| P1 | 转码状态机正确性 | **已完成（2026-09-17）**。数据库租约、代次/fencing token、续租、带条件完成写入和独立 HLS 尝试前缀已落地；真实双 Worker 抢占与崩溃恢复通过，见 `docs/reports/2026-09-17-p1-reliability-drill.md`。孤立对象清理和高档位超时补偿仍是后续项。 |
| P1 | 前端业务回归 | 补上传续传、发布播放、弹幕和互动的组件/页面测试，以及浏览器 E2E；将关键旅程并入 CI，失败时保留截图、日志和请求证据。 |
| P2 | 容量与编码矩阵 | **已完成基线（2026-09-18）**。读、写、弹幕、转码短时 Docker 基线通过；修复提交 `23f4a51` 已经真实验证。后续扩展 CPU、Intel QSV、NVIDIA NVENC 与软件回退矩阵。 |
| P3 | 部署与数据恢复 | 补 TLS/CDN、密钥管理和备份恢复演练；影子索引与 Alias 切换已落地，继续按真实部署需求评估 Helm/Kubernetes。 |
| P4 | 产品功能 | 在上述质量门禁稳定后，再按目标岗位和使用场景选择推荐、创作者中心、历史记录、审核体验等功能。 |

## 可执行任务清单

### 1. 校正资源门禁并复跑发布验证（P0）

**状态：已完成（2026-09-17）。** 真实 Docker 复验已取得 `passed=true`，详细结果见 `docs/reports/2026-09-17-release-validation-pass.md`。

- **现状：** `docker-compose.service.yml` 给视频服务和可选转码 Worker 各配置了 `4.00` CPU。历史报告的 424.39% 是 Docker 原始 CPU 百分比，不能直接解释为单核资源超限。
- **改动：** `scripts/loadtest/resource-normalization.ps1` 读取容器 CPU 配额，用 `raw / quotaCores` 计算配额使用率；不能读取配额时门禁失败。写链路分别采集视频服务和运行中的专用 Worker，并在报告中保留原始值、配额核数、归一化峰值和角色汇总。1 核、4 核样本校验已通过。FFmpeg 子进程、任务队列深度和每档转码耗时仍是后续观测项。
- **验收：** 完整发布复跑的五个步骤均为 `PASSED`；本地证据覆盖后端验证、真实 E2E、三类故障演练、k6 写链路、资源采样和 MQ 积压。远端 CI 和生产部署仍按环境单独验收。

### 2. 修复转码完成时的双写窗口（P0）

**状态：** 已于 2026-09-17 完成事务化修复与故障注入验证，记录见 `docs/reports/2026-09-17-transcode-result-transaction.md`。

- **现状：** `VideoTranscodeConsumer.publishResult` 先调用 `VideoTranscodeTaskMapper.markSuccess`，再调用 `VideoMapper.updateMediaByFileMd5`，中间没有事务。如果第二步抛错，任务可能已是成功状态，后续消息在入口直接跳过，视频播放地址无法靠重试修复。
- **改动：** 将两次 MySQL 更新移入独立 Spring Service 的事务方法，由消费端调用；先验证任务当前代次，再一次事务提交任务状态与所有关联视频的播放地址。MinIO 产物与数据库仍是最终一致，需要保留失败后对账修复入口。保留低清晰度先发布的阶段语义，不让后续档位失败清空已可播放结果。
- **验收：** 注入第二次数据库写入异常，验证任务状态回滚且可重试；重复消息和后续档位失败时，视频仍可播放且结果不倒退。

### 3. 给转码 Worker 增加代次保护（P1）

**状态：** 数据库租约、代次写入条件、心跳续租和独立 HLS 产物前缀已实现；旧 Worker 超时后的数据库竞争与对象隔离测试通过。真实容器双 Worker 抢占与崩溃恢复已通过，记录见 `docs/reports/2026-09-17-transcode-lease-fencing.md` 和 `docs/reports/2026-09-17-p1-reliability-drill.md`。

- **机制：** Flyway V6 增加 `claim_generation`、`claim_token` 和 `lease_until`。领取、续租、失败和完成均由数据库条件更新保护；低清晰度与最终结果写库仍保持同一事务。每次尝试使用独立 MinIO 产物前缀。
- **后续验收：** 继续覆盖 MQ 重投、旧 Worker 迟到及租约到期回收的长时运行；补孤立尝试对象清理与高档位超时补偿。

### 4. 扩大前端回归范围（P1）

- **现状更新（2026-09-17）：** 分片续传和弹幕连接逻辑已抽出并加入单元测试；Playwright 浏览器测试覆盖登录、上传、发布、播放、弹幕，以及 401 刷新和分片中断恢复。独立 CI 工作流在 push/PR 自动执行，见 `docs/reports/2026-09-17-frontend-regression.md`。本地 Chromium 测试通过，远端 CI 结果需以对应提交运行记录为准。
- **改动：** 先抽离上传状态机/API 与弹幕连接逻辑，分别测试断点续传、重复分片、401 刷新和 WebSocket 重连；再用浏览器 E2E 覆盖“登录 → 上传 → 发布 → 播放 → 弹幕/互动”及失败恢复。将关键旅程接入 CI，并保存失败截图和接口日志。
- **验收：** 核心路径及至少一个中断恢复场景自动通过，覆盖率指标包含上述业务模块；不以工具组件的高覆盖率替代页面质量。

### 5. 索引无空窗重建与生产运维（P2）

- **现状更新（2026-09-17）：** 已实现 MySQL 游标分页、影子索引、对账后 Alias 原子切换、保留旧索引回退和 MySQL 命名锁跨实例协调。双 Canal 容器端到端切换与 CDC 对账已通过；规模化压测和锁等待 SLO 仍待完成，见 `docs/reports/2026-09-17-es-shadow-rebuild.md` 与 `docs/reports/2026-09-17-p1-reliability-drill.md`。
- **改动：** 按 ID 分页读取 MySQL，写入带版本号的新索引；追平增量后对账，使用 ES Alias 原子切换，保留旧索引至回滚窗口结束。另做备份恢复、TLS/CDN 和密钥注入演练。
- **验收：** 重建过程中查询仍返回旧索引结果；切换后对账一致，失败可切回旧索引；用规模化数据验证内存上限与耗时。

## 简历/答辩取材

优先讲三条：**分片上传到 HLS 播放**、**Outbox/Inbox/DLQ 故障恢复**、**WebSocket 弹幕实时广播与异步持久化**。每条按“业务问题 → 关键设计 → 失败窗口与恢复 → 验证证据 → 当前边界”展开。读链路 300 RPS 只作为有环境和范围限定的性能证据。
