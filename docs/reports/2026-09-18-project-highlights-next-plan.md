# Bilibili Cloud 项目亮点与下一步提升计划

核对日期：2026-09-18；源码核对基线 `afd4817`。依据当前源码、已提交复验记录和本地原始 JSON 整理。工作区有其他未提交改动，本报告不把它们算作已交付能力。

## 项目定位与可展示亮点

这是覆盖视频上传、处理、播放、弹幕、互动和搜索的 Java 微服务练习项目。适合按“业务约束 → 失败窗口 → 恢复机制 → 验证证据”介绍，避免只罗列组件。

| 亮点 | 业务问题与实现 | 当前证据及边界 |
| --- | --- | --- |
| 上传到播放闭环 | `UploadServiceImpl` 支持秒传、分片续传；MinIO 保存对象，RocketMQ 触发 FFmpeg 多档 HLS。`VideoTranscodeResultService` 在一个 MySQL 事务中更新任务结果和视频地址；数据库租约、代次及独立 `attempt-*` 前缀防止旧 Worker 覆盖新结果。首档可播放后，高档位可独立补偿；历史失效尝试按引用、租约及保留期清理。 | 本地双 Worker 演练中，首档 HLS 可读取后领取者崩溃，代次 2 补偿期间首档仍可读取，最后切换完整 HLS。清理及补偿另有 MySQL/MinIO 集成测试。跨 MySQL 与 MinIO 仍是最终一致，未注入 FFmpeg 错误或网络超时。 |
| 可恢复的异步消息 | 通用 Outbox、Inbox、有限重试、应用 DLQ 与重放处理消息失败和重复投递；转码任务另有持久化领取与超时恢复。 | P0 本地 Compose 故障演练覆盖 RocketMQ、Redis、Elasticsearch。语义是至少一次投递与消费幂等，不是 Exactly Once。 |
| 实时弹幕与异步落库 | Netty WebSocket 按房间推送，Redis Pub/Sub 跨实例广播，RocketMQ 异步持久化；一次性 Ticket 保护连接。 | P0 真实 Compose E2E 覆盖弹幕链路；Pub/Sub 不提供离线可靠投递。 |
| 可重建的搜索读模型 | MySQL 为事实源；Canal 同步 Elasticsearch，影子索引分页构建、对账、Alias 原子切换并保留旧索引回退。切换追平与 CDC 写入使用 MySQL 命名锁协调多 Canal 实例；晚到的视频事件按 MySQL 当前状态刷新，避免索引回退。 | 本地双 Canal 演练中，20 次更新全部捕获并消费、2.659 秒追平；已捕获未消费峰值 14 条，CDC 单次最大锁等待约 962 ms；Alias 只指向一个索引且对账一致。该短突发结果不是容量 SLO。 |
| 可复现的工程验证 | 后端 Maven/Testcontainers/JaCoCo、前端 Vitest/Playwright、Compose E2E、故障演练、k6 和资源/MQ 门禁形成分层验证。 | P0 发布复验、本地 P1 演练和 P2 四场景短时基线均通过；前端两条浏览器旅程使用 API/WebSocket 桩。均不能外推为远端 CI、生产可用性或生产容量。 |

## 已验证到什么程度

- P0：2026-09-17 本地 `release-validation.json` 的 `passed=true`，后端验证、真实 Compose E2E、故障演练、2 分钟写链路 SLO、资源和 MQ 门禁通过。见 [发布复验](2026-09-17-release-validation-pass.md)。
- P1：2026-09-17 本地 `p1-reliability-drill.json` 的 `passed=true`、`cleanupPassed=true`。双 Worker 崩溃接管与双 Canal 索引切换通过。见 [故障演练](2026-09-17-p1-reliability-drill.md)。
- P1 增量：2026-09-18 本地“首档可播放后崩溃”演练通过，见 [转码补偿记录](2026-09-18-transcode-attempt-cleanup-rendition-recovery.md)；双 Canal 20 次更新下的锁等待与 CDC 追平完成单次量化，九模块 94 个测试通过，见 [索引切换量化](2026-09-18-index-cutover-lock-cdc-backlog.md)。
- P2：2026-09-18 本地 `p2-capacity-baseline.json` 的 `passed=true`。30 秒测量中，读约 150 HTTP RPS、写 1 operation/s、弹幕 1 message/s、转码 1 task/s（双 Worker）分别通过当前阈值；转码 30 次完成。读负载提高到约 300 HTTP RPS 的另一次试跑触发 Gateway 98.97% CPU 配额峰值，未通过 85% 门槛。见 [容量报告](2026-09-18-p2-capacity-baseline.md)。
- 上述 P2 测量绑定提交 `9fcd049` 和当时的未提交工作区；本轮仅核对现存原始报告，没有重新运行 Docker、k6 或远端 CI。

## 下一步提升计划

| 顺序 | 模块与具体动作 | 完成标准 |
| --- | --- | --- |
| 1（已实现） | `bilibili-video-service`：失效 `attempt-*` 对象按引用、租约和保留期清理；高档位有独立补偿状态、有限自动重试和管理员重排队入口。 | H2、真实 MySQL/MinIO 测试及 P1 双 Worker 崩溃接管证据见 [实施记录](2026-09-18-transcode-attempt-cleanup-rendition-recovery.md)。真实容器的“低清已发布后高档位超时”注入仍是单独验证项。 |
| 2（已完成单次量化） | `bilibili-canal-service`：已记录命名锁等待、CDC 阶段积压和追平耗时，并修复切换立即抢锁失败与旧事件覆盖新索引的问题。下一步在固定数据规模、持续写入和目标资源配额下重复切换实验。 | 当前 20 次更新演练通过；后续报告需给出多次运行的锁等待 P95/P99、积压峰值、恢复时间和搜索可用性，再设容量门槛。 |
| 3 | `scripts/loadtest` 与部署配置：在目标 CPU/内存配额、数据规模和持续时间下复跑读、写、弹幕、转码；补软件编码、QSV、NVENC 的可用环境矩阵。 | 每组报告绑定 SHA、硬件/编码器、配额、数据集、预热、持续时间、P95/P99、错误率、MQ 与资源曲线；明确可承载负载，不把本地短时基线直接用于容量承诺。 |
| 4 | 部署与运维：按实际部署方案补 MySQL/MinIO 备份恢复、密钥注入、TLS/CDN 配置与恢复演练。 | 在隔离环境从备份恢复用户、视频元数据和对象，验证播放与搜索对账；记录恢复用时、数据丢失窗口及失败处理步骤。 |

产品功能如推荐、创作者工具和审核体验，等目标用户与需求确定后再单独排期，不以增加功能替代上述可靠性证据。

## 简历与答辩取材

优先选“上传到 HLS 播放及 Worker 崩溃接管”“Outbox/Inbox 与故障恢复”“弹幕实时广播与异步落库”三条。每条说清自己负责的代码、业务不变量、失败恢复和对应报告。性能数字必须附本地环境、时长、负载和提交；不要写成生产指标。
