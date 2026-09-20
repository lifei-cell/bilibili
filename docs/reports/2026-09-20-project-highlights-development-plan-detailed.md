# Bilibili Cloud 项目亮点与开发计划详细版

复核日期：2026-09-20。本文是 [项目复核概要](./2026-09-20-project-review.md) 的展开版，面向项目答辩、简历取材和后续开发排期。

## 1. 项目定位与证据口径

Bilibili Cloud 是一个 Java 21/Spring 微服务视频平台，业务主线覆盖上传、转码、审核、播放、弹幕、互动和搜索。它当前最值得展示的不是服务数量，而是对异步链路失败、并发接管和读模型重建做了工程化处理。

证据分四层使用：

1. 当前源码和本轮测试：用于说明当前实现和能直接复验的行为。
2. 已提交的本地 Docker/Testcontainers 报告：用于说明特定提交、环境和故障场景下曾通过。
3. 远端 CI：只有实际运行记录才能证明，工作流配置本身不等于通过。
4. 生产结论：当前没有生产流量、生产容量、长期可用性或生产灾备证据，不能外推。

一句话定位：这是一个以视频业务为载体、重点展示异步一致性和故障恢复能力的本地工程化项目，而不是生产级 B 站复刻。

## 2. 亮点一：上传、转码到播放的完整媒体闭环

### 业务问题

大文件上传会被弱网和页面刷新打断；转码耗时长，Worker 可能中途崩溃；多 Worker 重试时，旧任务可能迟到覆盖新结果；首档已经可播放后，高档位失败不应让视频整体不可用。

### 核心业务不变量

- 一个分片必须同时匹配用户、上传任务、文件 MD5、分片序号和总分片数。
- 未收齐或顺序错误的分片不能合并为源文件。
- 只有当前持有数据库租约、代次和令牌的 Worker 能续租或提交结果。
- 旧代次产生的 `attempt-*` HLS 不能覆盖新代次结果。
- 首档已发布后，高档位补偿失败不能破坏当前可播放地址。
- 正在使用、有有效租约或仍在保留期内的媒体对象不能被清理。

### 当前实现

1. [UploadServiceImpl](../../bilibili-video-service/src/main/java/com/gary/bilibili/video/service/impl/UploadServiceImpl.java) 处理秒传检查、上传任务复用、分片 MD5 校验、断点查询、分片落 MinIO 和合并。
2. 大文件还可通过 [DirectUploadService](../../bilibili-video-service/src/main/java/com/gary/bilibili/video/service/DirectUploadService.java) 使用预签名地址直传，降低应用服务的数据转发压力。
3. 合并完成后写入持久化转码任务，由 [VideoTranscodeTaskPublisher](../../bilibili-video-service/src/main/java/com/gary/bilibili/video/producer/VideoTranscodeTaskPublisher.java) 领取并生成 `claim_generation`、`claim_token` 和租约。
4. [VideoTranscodeConsumer](../../bilibili-video-service/src/main/java/com/gary/bilibili/video/consumer/VideoTranscodeConsumer.java) 调用 FFmpeg 生成多档 HLS；硬件编码失败可降级到 `libx264`。
5. [VideoTranscodeResultService](../../bilibili-video-service/src/main/java/com/gary/bilibili/video/service/VideoTranscodeResultService.java) 在一个 MySQL 事务内更新转码任务和视频播放地址，提交条件带当前代次和令牌。
6. 首档成功后，高档位通过独立的 `rendition_status`、有限重试和管理员重排队继续补偿。
7. [HlsAttemptCleanupTask](../../bilibili-video-service/src/main/java/com/gary/bilibili/video/service/HlsAttemptCleanupTask.java) 按引用、租约、保留期和枚举上限清理历史尝试对象。

### 架构取舍

最小方案是上传完成后同步调用 FFmpeg，但会占用请求线程、无法安全接管，也难以控制超时。当前方案用数据库任务加 MQ 解耦请求和计算，并用数据库条件更新实现 fencing。代价是状态机、对象清理和最终一致性明显更复杂。

MySQL 事务只能保证任务表和视频表一致，不能同时事务性提交 MinIO。因此系统采用“先写独立尝试前缀、再条件提交数据库引用、最后清理孤儿对象”的补偿模型，而不是声称跨存储强一致。

### 已有证据与边界

- 真实 MySQL/MinIO 集成测试覆盖租约接管、旧代次拒绝、补偿状态和对象清理。
- 双 Worker 演练覆盖首档可播放后原 Worker 崩溃、代次 2 接管以及补偿期间原地址保持可读。
- 尚缺 FFmpeg 非零退出、进程挂起、MinIO 部分写入和网络超时的真实容器故障矩阵。

### 答辩表达

不要说“使用 RocketMQ 和 FFmpeg 实现转码”。应说：“转码是长任务，我把任务领取权落在 MySQL，用代次、令牌和租约隔离旧 Worker；每次尝试写独立 MinIO 前缀，只有条件更新成功后才切换播放地址，因此崩溃接管时旧结果不会覆盖新结果。”

## 3. 亮点二：至少一次消息下的幂等、失败收敛与重放

### 业务问题

业务写库成功但 MQ 发送失败会丢事件；Broker 或消费者重试会造成重复消费；持续失败的消息如果无限重试，会阻塞消费并放大故障。

### 核心业务不变量

- 业务状态和待发送事件必须在同一个本地事务内提交。
- 相同消费者组、Topic 和消息键只能成功执行业务副作用一次。
- 重试必须有上限；耗尽后要留下可查询、可审计、可重放的失败记录。
- 重放仍要经过幂等检查，不能绕开正常消费约束。

### 当前实现

- [OutboxEventService](../../bilibili-common/src/main/java/com/gary/bilibili/common/reliability/OutboxEventService.java) 和 [OutboxEventPublisher](../../bilibili-common/src/main/java/com/gary/bilibili/common/reliability/OutboxEventPublisher.java) 负责事务 Outbox 与异步投递。
- [MessageInboxRepository](../../bilibili-common/src/main/java/com/gary/bilibili/common/reliability/MessageInboxRepository.java) 记录消费身份，避免重复副作用。
- [ReliableMessageExecutor](../../bilibili-common/src/main/java/com/gary/bilibili/common/reliability/ReliableMessageExecutor.java) 统一有限重试、Inbox 和失败落库。
- [FailedMessageAdminService](../../bilibili-common/src/main/java/com/gary/bilibili/common/reliability/FailedMessageAdminService.java) 提供应用 DLQ 查询和重放入口。
- 视频转码使用独立的持久化任务状态机，因为它还需要长租约、心跳、进度和媒体结果提交，不能被通用 Outbox 简化掉。

### 架构取舍

项目选择“至少一次 + 业务幂等 + 最终一致”，没有追求跨 MySQL、RocketMQ 和 Redis 的 Exactly Once。这样更符合常见工程现实，但要求为每种消费副作用设计稳定消息键和幂等边界。

通用可靠消息组件适合短业务事件；长耗时转码保留专用任务表是合理的边界，避免把两种生命周期强行塞进一个超大框架。

### 已有证据与边界

本地故障演练覆盖 RocketMQ 暂停和恢复、Outbox/Inbox 对账及应用 DLQ。当前证据不能证明跨地域 Broker 故障、长时间积压或生产消费能力。

### 答辩表达

重点解释为什么不是 Exactly Once：“MQ 可能重复投递，所以可靠性的核心不是避免重复，而是让重复不改变最终业务结果；Outbox 解决发送丢失，Inbox 解决重复副作用，DLQ 解决失败收敛与人工恢复。”

## 4. 亮点三：实时弹幕的低延迟广播与可靠落库分层

### 业务问题

弹幕既需要实时到达同房间用户，又需要跨实例广播和最终落库。如果每条弹幕同步写库再广播，延迟和数据库压力都会上升；如果只用 Pub/Sub，又会丢失离线消息。

### 核心业务不变量

- 登录用户的 WebSocket 凭据不能长期暴露在 URL 中，并且连接凭据只能使用一次。
- 同实例连接按视频房间隔离，不能串房。
- Redis Pub/Sub 只承担实时跨实例广播，不能被当成可靠存储。
- RocketMQ/数据库承担持久化，重复消息仍需用请求标识幂等。

### 当前实现

- [DanmuWebSocketTicketService](../../bilibili-danmu-service/src/main/java/com/gary/bilibili/danmu/service/DanmuWebSocketTicketService.java) 生成短期一次性 Ticket。
- [DanmuRoomManager](../../bilibili-danmu-service/src/main/java/com/gary/bilibili/danmu/netty/DanmuRoomManager.java) 和 Netty Handler 管理房间连接与推送。
- Redis Pub/Sub 扩散到其他弹幕实例；RocketMQ 将弹幕交给 [DanmuPersistConsumer](../../bilibili-danmu-service/src/main/java/com/gary/bilibili/danmu/consumer/DanmuPersistConsumer.java) 批量持久化。
- 前端连接封装具有重连逻辑，浏览器旅程覆盖断开后恢复接收。

### 架构取舍与边界

当前设计把“在线实时性”和“最终持久性”拆开，避免要求 Redis Pub/Sub 提供它不具备的离线可靠投递。代价是广播成功和落库成功之间存在短暂不一致，需要监控 MQ 积压和持久化失败。

后续只有在明确需要“用户重连后补齐错过弹幕”时，才值得增加基于数据库游标或 Redis Stream 的补拉机制；不能仅为了技术复杂度替换现有 Pub/Sub。

## 5. 亮点四：以 MySQL 为事实源的可重建搜索读模型

### 业务问题

Elasticsearch 适合检索，但不应成为视频发布状态的事实源。增量 CDC 可能延迟或乱序；直接清空重建会导致搜索不可用；多 Canal 实例并发切换可能覆盖彼此结果。

### 核心业务不变量

- 视频发布状态以 MySQL 为准，ES 可以删除并重建。
- 构建影子索引时，线上查询继续使用旧 Alias。
- 切换前必须追平并对账；Alias 最终只能指向一个物理索引。
- 多实例只有一个切换成功，较晚完成的旧重建不能覆盖新 Alias。
- 晚到 CDC 事件不能用旧快照覆盖 MySQL 中的新状态。

### 当前实现

1. [PublishedVideoSource](../../bilibili-canal-service/src/main/java/com/gary/bilibili/canal/service/PublishedVideoSource.java) 按 ID 分页读取 MySQL 已发布视频。
2. [SearchIndexMaintenanceService](../../bilibili-canal-service/src/main/java/com/gary/bilibili/canal/service/SearchIndexMaintenanceService.java) 创建版本化影子索引、分页填充、删除孤儿文档并对账。
3. 构建阶段不持有全局锁；最终追平、对账和 Alias 切换才进入短临界区。
4. [MySqlNamedLock](../../bilibili-canal-service/src/main/java/com/gary/bilibili/canal/service/MySqlNamedLock.java) 在同一数据库连接上持有跨实例命名锁，并记录等待和持有时间。
5. [VideoIndexAlias](../../bilibili-canal-service/src/main/java/com/gary/bilibili/canal/service/VideoIndexAlias.java) 在切换前检查预期旧索引，防止并发重建覆盖已经改变的 Alias。
6. 视频 CDC 只使用事件中的 ID 定位对象，持锁后回查 MySQL 当前状态再写 ES，消除晚到旧事件造成的索引回退。

### 架构取舍

如果在整个影子索引构建期间持全局锁，CDC 会长时间阻塞；如果完全不协调，最终追平和切换存在竞态。当前方案只锁最终临界区，在可用性和一致性之间取得平衡。

MySQL 命名锁复用了已有基础设施，适合当前单数据库部署；如果未来跨数据库或锁等待成为瓶颈，再评估独立协调服务。现在直接引入 ZooKeeper/etcd 成本高于收益。

### 已有证据与边界

双 Canal、20 次并发更新的单次演练记录了锁等待、积压和 2.659 秒追平，并验证 Alias 唯一及最终标题可搜索。它不能代表持续写入下的 P95/P99 或大数据量重建能力。

### 答辩表达

突出“ES 可丢弃、MySQL 可恢复”：“我把搜索定义成读模型。重建时先在影子索引离线填充，最后用短命名锁追平、对账并原子切 Alias；即使 CDC 乱序，也回源 MySQL 当前状态，避免旧事件把新标题覆盖掉。”

## 6. 亮点五：可观测性与故障恢复入口

[RequestObservabilityFilter](../../bilibili-common/src/main/java/com/gary/bilibili/common/observability/RequestObservabilityFilter.java) 统一传递或生成 Request ID，返回 Trace ID，把相关字段写入 MDC，并记录 `bilibili.api.duration`。Prometheus、Grafana、Loki、Tempo、Promtail 和 RocketMQ Exporter 组成指标、日志、Trace 与消息积压观察面。

这套能力的价值不是“搭了监控组件”，而是让故障演练有可判定的恢复条件：请求错误率、延迟、资源配额、MQ 积压、Outbox/Inbox 状态和索引对账都能成为门禁。当前仍缺真实告警触发、通知、值班确认和恢复复盘形成的闭环证据。

## 7. 亮点六：分层质量门禁与证据边界

当前质量体系分为：

- 单元测试：状态机、条件更新、命令构建、消费幂等和前端核心工具。
- 集成测试：真实 MySQL/MinIO/Testcontainers，验证数据库迁移、事务和对象存储行为。
- 浏览器旅程：登录、上传、发布、播放、弹幕重连、401 刷新和中断续传。
- Compose E2E：使用真实 MySQL、Redis、RocketMQ、MinIO、Elasticsearch 和服务容器跑核心链路。
- 故障演练：停止组件或 Worker，验证接管、追平、对账和清理。
- 容量基线：k6 配合资源和 MQ 采样，并按容器 CPU 配额归一化。

本轮实际复验为后端 90 个单元测试、前端 14 个测试和 2 条 Playwright 旅程通过。完整 `clean verify` 因 Docker Desktop Engine 未运行而被 Testcontainers 阻断，所以只能引用历史 94 测试报告，不能把它写成本轮通过。

## 8. 开发原则与优先级

开发顺序遵循四条原则：

1. 先让已有能力在干净提交和远端环境可重复，再增加功能。
2. 先测出瓶颈和失败窗口，再决定缓存、扩容或重构方案。
3. 每项任务必须同时交付代码、自动化验证、运行文档和报告证据。
4. 新组件只有在现有方案达到明确阈值后才引入，避免为了技术栈丰富度增加运维面。

依赖顺序：交付基线 → 容量画像 → 故障矩阵与灾备 → 搜索运维化/前端优化 → 业务差异化。

## 9. 阶段一：收敛交付基线与远端 CI（P0，2–3 天）

### 模块与改动

- 当前工作区：逐项审查既有暂存和未暂存改动，按“写链路基线、E2E 修复、编码器配置”等独立能力拆分提交。
- `.github/workflows/ci.yml`：让后端 `verify`、前端 `quality`、浏览器旅程、Compose 校验和计划性发布验证都有清晰超时、失败日志和证据产物。
- `scripts/verification`：执行前记录 SHA、工具版本、Docker Context、Compose 展开配置和运行参数；失败也必须落 JSON/Markdown 汇总。
- 文档：明确哪些门禁在 push/PR 运行，哪些只在 schedule/manual 运行，以及各自耗时和依赖。

### 验收标准

- 工作树干净，提交按完整能力拆分，没有把无关改动混入一个提交。
- 同一 SHA 上本地 `clean verify`、`npm run quality`、`npm run test:e2e` 通过且零跳过。
- 远端 CI 实际全绿，发布验证产物能下载并包含环境、测试和故障阶段结果。
- 任何环境阻断都报告为 `BLOCKED`，不伪装成应用通过或失败。

## 10. 阶段二：建立持续容量画像并定位 Gateway 瓶颈（P0，4–6 天）

### 已知问题

历史 30 秒试跑在约 300 HTTP RPS、零请求错误时，Gateway CPU 配额峰值达到 98.97%，超过 85% 门槛。这个结果只能说明当前短时饱和信号，不能直接判断是日志、鉴权、路由、连接池、下游响应还是 CPU 配额过小。

### 模块与改动

- `scripts/loadtest`：把 150/200/250/300 HTTP RPS 做成固定阶梯，每档预热 5 分钟、测量 15–30 分钟，至少重复 3 次。
- Gateway：补 JVM、GC、线程池、连接池、事件循环、路由和下游调用分解指标；必要时采集 JFR/async-profiler。
- 报告：绑定数据规模、缓存冷热、容器 CPU/内存、并发连接、请求组合和 SHA。
- 优化决策按证据选择：CPU 热点才优化过滤器/日志/序列化；等待热点才调连接池或下游；配额不足才调整资源；不要预先假设加缓存能解决。

### 验收标准

- 每档负载给出吞吐、P50/P95/P99、HTTP/业务错误率、Gateway 与下游 CPU/内存、GC、连接池和 MQ 曲线。
- 定位 300 RPS 饱和的主要贡献项，并用优化前后同条件 A/B 数据证明改进。
- 定义“稳定承载负载”和“饱和点”，达到门槛前不对外写生产 QPS。

## 11. 阶段三：补齐转码和消息故障矩阵（P0/P1，4–5 天）

### 模块与改动

- `bilibili-video-service`：为 FFmpeg 进程执行和对象存储访问建立可注入故障边界，覆盖非零退出、超时、进程挂起、上传中断和部分对象残留。
- `scripts/e2e/p1-reliability-drill.ps1`：加入 Worker 网络隔离、消息重复投递和首档发布后高档位失败场景。
- 诊断产物：每次失败保存任务行、租约、代次、对象列表、Worker 日志和当前播放地址。

### 验收标准

- 每个故障点都有自动化测试或真实容器演练，不依赖固定 sleep 判定成功。
- 已发布低清保持可读，旧代次更新影响 0 行，新代次可接管。
- 自动补偿耗尽后进入可查询失败态，管理员重排队不改变当前可播放地址。
- 孤儿对象在保留期后删除，引用对象、有效租约对象和超上限对象不会误删。

## 12. 阶段四：灾备、安全和发布运维（P0，3–5 天）

### 模块与改动

- 新增备份/恢复脚本：MySQL 做一致性备份，MinIO 保存对象和版本信息；恢复后通过 MySQL 重建 ES，不备份 ES 作为事实源。
- 配置：生产 profile 禁止默认密码和默认管理员 Token；密钥通过部署环境注入并支持轮换。
- 入口：补 TLS/CDN/对象访问域名配置，验证预签名 URL 的 Host 与过期策略。
- CI：增加依赖、容器镜像、Secret 和 SBOM 扫描，高危漏洞或泄密阻断发布。

### 验收标准

- 在隔离环境完成一次“删除后恢复”：恢复用户、视频元数据和 MinIO 对象，重建 ES，验证登录、播放和搜索。
- 报告 RTO、RPO、数据校验方法和失败回滚步骤。
- 生产配置缺少密钥时启动失败；仓库和镜像不包含真实凭据；高危扫描结果有门禁与例外流程。

## 13. 阶段五：搜索重建运维化与前端性能（P1，4–6 天）

### 搜索重建

最小改动是把“重建已在运行”映射为 409，并暴露当前阶段和计数；只有重建时间超过网关超时、需要跨进程恢复或人工审计时，再增加持久化任务表。

若达到升级阈值，增加 `search_maintenance_job`：保存 jobId、类型、目标索引、状态、分页游标、计数、错误、发起人和时间；接口返回 202，由后台 Worker 执行，并继续使用 MySQL 命名锁保护最终切换。

验收标准：并发请求返回可解释的 202/409；可查询进度和失败原因；Alias 始终唯一；失败可清理影子索引或回滚；固定数据规模和持续写入下重复运行，给出锁等待 P95/P99、积压峰值和追平时间。

### 前端性能与覆盖

- 分析 `VideoView` 约 607 KB chunk，优先异步加载 `hls.js` 和低频功能，再考虑 Rollup `manualChunks`。
- 当前覆盖率只统计少量核心文件，先把口径扩到认证、上传、播放、管理和路由逻辑，记录真实基线后再设增量门槛。
- Playwright 增加移动视口、慢网、上传刷新恢复、播放失败降级和管理员审核旅程。

验收标准：定义首屏和播放器 chunk 预算；关键页面性能无明显回退；核心用户旅程在桌面和移动视口通过；失败保留截图、Trace 和 API 日志。

## 14. 阶段六：增加一条业务差异化主线（P2，1–2 周）

### 推荐主线：创作者数据中心

先做创作者数据中心而不是完整推荐系统。它可以复用现有播放、点赞、收藏、评论和视频状态数据，突出 Java 后端的数据口径、聚合、缓存和权限设计，新增复杂度可控。

最小数据模型为按视频和日期唯一的 `video_daily_metrics`，聚合播放、点赞、收藏、评论和新增粉丝；提供趋势、Top 视频和时间范围查询。每日聚合任务必须幂等，迟到事件可以重算，查询失败可回退到最近已完成快照。

验收标准：指标定义有文档；同一日期重复聚合结果不变；跨天和迟到数据有测试；创作者只能访问自己的数据；接口在目标数据规模下满足延迟门槛；前端展示与数据库抽样一致。

### 可选 AI 差异化：内容审核助手

如果需要强化 AI 应用后端方向，可在现有人工审核前增加异步审核助手，而不是让模型直接下架内容。任务记录模型/供应商版本、输入摘要、规则命中、置信度和建议；低置信度、超时或供应商失败一律进入人工审核，管理员决定才改变发布状态。

验收标准：外部调用有显式开关、超时、限流和脱敏；结果可审计、可重放、可人工覆盖；固定评测集给出召回率、误报率和成本，未达到阈值时不自动处置。

## 15. 建议里程碑

| 里程碑 | 交付结果 | 退出条件 |
| --- | --- | --- |
| M1 可重复交付 | 干净提交、远端 CI、完整证据产物 | 同一 SHA 的本地与远端核心门禁通过 |
| M2 容量可解释 | 持续压测、Gateway 瓶颈定位和 A/B 报告 | 明确稳定负载、饱和点及主要瓶颈 |
| M3 故障可恢复 | 转码故障矩阵、消息恢复和诊断快照 | 失败自动收敛且已发布播放不回退 |
| M4 数据可恢复 | 备份恢复、安全扫描和发布配置 | 隔离恢复演练通过并记录 RTO/RPO |
| M5 运维可操作 | 搜索任务状态、前端性能和扩展 E2E | 运维动作有状态、冲突语义和失败证据 |
| M6 业务可展示 | 创作者数据中心或审核助手 | 有明确用户价值、指标和端到端验收 |

## 16. 暂不建议投入

- 不要因为“微服务项目应该有”而引入 Kubernetes、Service Mesh、另一个 MQ 或独立分布式锁服务。
- 不要把短时本地压测数字写成生产 QPS，也不要用平均延迟替代 P95/P99。
- 不要为提高覆盖率只增加无业务断言的测试；优先覆盖并发、重试、恢复和权限边界。
- 不要同时做完整推荐系统、复杂大数据链路和 AI 审核；先完成一个可验证的业务闭环。
- 不要把 Redis、Elasticsearch 或 MQ 当事实源；用户、视频状态和恢复边界继续以 MySQL 为准。

完成 M1–M4 后，项目的提升重点才应从“可靠性证明”转向“业务差异化”。这条顺序对 Java 后端实习答辩的收益高于继续扩充技术名词。
