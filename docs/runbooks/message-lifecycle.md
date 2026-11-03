# 消息数据生命周期

## 保留边界

| 数据 | 默认窗口 | 到期动作 |
| --- | --- | --- |
| Outbox `PUBLISHED` | 发布成功后 7 天 | 单事务复制完整记录到 `reliable_event_outbox_archive`，再删除热表记录 |
| Inbox `SUCCEEDED` | 消费成功后 30 天 | 单事务保存消费组、Topic、业务键及成功时间到 `mq_inbox_receipt`，再删除热表明细 |
| Outbox 未完成 / Inbox 失败或处理中 | 无自动删除 | 保留排障、接管和重试依据 |
| Outbox 归档、Inbox 去重凭证、重放审计 | 不自动删除 | 保留历史事件身份和重放防重依据 |

30 天是热表明细保留窗口，不是幂等失效时间。目前业务没有可信的“旧消息绝不再出现”上限；MQ 历史消息、人工 DLQ 重放和历史数据恢复都可能晚于热表保留期。只保存一个时间窗口后彻底删键不能保证不重复执行业务。因此永久保存精简去重凭证，空间仍随唯一成功消息数增长；这是明确的安全与容量取舍，不是无限数据零成本。

消费领取前检查凭证，热表插入成功后再做当前读检查，覆盖“第一次检查后刚好归档”的并发窗口。凭证当前读不受已有可重复读快照影响。成功状态查询也查询凭证。归档只处理成功记录，按批锁定源记录并使用 `SKIP LOCKED`，复制失败则整批回滚，不存在先删后归档的空窗。

Outbox 归档仍保留 `event_id` 唯一身份，追加同一事件会被拒绝重新入队；不会把归档后的历史事件当成新事件。不要手工删除去重凭证、清空归档表或给旧业务消息生成新业务键。消费组、Topic、业务键共同定义去重身份；更换消费组重新订阅属于新身份，不受旧凭证保护。确需重做已成功业务，应走单独的业务补偿设计。

## 配置与容量

配置统一在 `observability.yml`，所有共享数据库的实例必须使用一致的保留策略。

| 环境变量 | 默认值 | 含义 |
| --- | ---: | --- |
| `MESSAGE_LIFECYCLE_ENABLED` | true | 启停归档，不影响正常消费、去重和耗尽状态标记 |
| `OUTBOX_RETENTION_DAYS` | 7 | Outbox 热表保留天数，至少 1 |
| `INBOX_RETENTION_DAYS` | 30 | Inbox 热表保留天数，至少 1 |
| `MESSAGE_LIFECYCLE_BATCH_SIZE` | 200 | 每轮各表最多处理条数，范围 1–1000 |
| `MESSAGE_LIFECYCLE_INTERVAL_MS` | 60000 | 两轮归档之间的间隔 |
| `MESSAGE_LIFECYCLE_INITIAL_DELAY_MS` | 60000 | 启动后的首次归档延迟 |
| `MESSAGE_MAX_REPLAYS` | 5 | 每条消息允许的人工重放次数上限 |
| `MESSAGE_REPLAY_COOLDOWN_SECONDS` | 60 | 同一消息两次人工重放最小间隔 |

归档任务仅在配置了数据库与 RocketMQ producer group 的服务中注册，这些服务须启用定时调度；每个服务只归档自己 owner 的 Outbox。Inbox 使用共享保留策略，多个实例通过行锁协作。默认单实例每分钟最多处理 200 条过期记录；按实际到期速率调整批量、间隔和实例数，并监测热表、归档表、凭证表容量。备份恢复必须一致地恢复业务数据、热表、归档、凭证和审计，不能仅恢复消息热表。

## 耗尽、查询与告警

发布器每轮将达到 `OUTBOX_MAX_ATTEMPTS` 的失败记录，或最后一次尝试已失效的处理中记录，标记为 `EXHAUSTED`。仍有效的最后一次租约不会被提前终结；旧领取者不能回写耗尽或重放后的记录。

所有接口须携带 `X-Admin-Token`，经 Gateway 路由到视频服务。以下查询返回元数据，不返回 Outbox 原始载荷：

- `GET /api/admin/mq/outbox/exhausted?afterId=0&size=20`
- `GET /api/admin/mq/outbox/archives?afterId=0&size=20`
- `GET /api/admin/mq/outbox/{id}/replays`
- `GET /api/admin/mq/failures?status=FAILED&page=1&size=20`
- `GET /api/admin/mq/failures/{id}/replays`

Outbox 列表按源记录 ID 游标分页，`size` 最大 100；游标用于浏览当前记录，不是归档变更订阅。重放审计查询返回最近 100 条。`bilibili_outbox_exhausted` 非零持续 2 分钟触发 `OutboxRetriesExhausted`；原有 `ApplicationDlqPending` 继续覆盖消费失败。归档失败计数和最后成功时间分别触发 `MessageLifecycleFailing`、`MessageLifecycleStalled`。监测到告警后先修复根因，再逐条重放。

## 受控重放

`POST /api/admin/mq/outbox/{id}/replay` 只接受 `EXHAUSTED`，恢复原记录为 `PENDING`，保持原事件 ID 与载荷。

`POST /api/admin/mq/failures/{id}/replay` 只接受 `FAILED`，在同一事务中创建一条待发送 Outbox 并把 DLQ 标为 `REPLAYED`。发布器校验并原样发送持久化 JSON，不加载来源服务的 Java 类，`payload_type` 留作审计元数据。`REPLAYED` 表示已持久化排队，不代表业务执行成功；排队的 Outbox 也可能耗尽，需要结合其 eventId 查库排障。成功消费或命中历史成功凭证会把匹配的 DLQ 标为 `RESOLVED`。新重放不会修改 Inbox 身份，也不会删除凭证。原有无请求体重放调用需要升级。

请求体示例（`expectedReplayCount` 从查询结果读取）：

```json
{
  "requestId": "operator-request-20261102-0001",
  "expectedReplayCount": 0,
  "reason": "已修复消息依赖故障并验证服务恢复，申请重放"
}
```

同一操作的网络重试必须复用原 requestId 和完整请求体，返回原排队凭据，不增加次数；新操作使用新的 requestId。请求编号长度 16–64，仅允许字母、数字、下划线、连字符；原因至少 10 个非外围空白字符，最多 500 字符。不同消息或不同参数复用编号会拒绝。行锁、版本、冷却和次数上限共同阻止并发重复操作。审计只记录获准操作，包含原始请求编号、来源、版本、原因、排队 eventId 和时间；它不将共享管理员令牌冒充为个人身份。

未授权返回 401，格式错误返回 400，状态/版本/上限冲突返回 409。不要把管理员密钥或其他敏感信息写入 reason。重放不直接调用 MQ，数据库提交后由常规发布器发送；发送与消费仍为至少一次语义。

## 升级与验证

V11 是增量迁移。上线前必须停止所有旧版消息消费者和发布器，完成迁移并启动新版后再启用归档；旧版本不会检查去重凭证，不能与归档任务混跑。第一次启用可先设置 `MESSAGE_LIFECYCLE_ENABLED=false`，确认版本一致后启用。回退时仅关闭归档不够：已压缩数据仍需要新消费者识别，不能直接回退到不查凭证的代码。

`MessageArchiveMySqlIT` 验证保留窗口、复制失败回滚、归档与领取交错、旧快照和并发归档；`MessageReplayMySqlIT` 验证耗尽、有效租约保护、并发重放、幂等请求、冷却/次数限制、事务失败回滚、DLQ 载荷和去重身份保留。控制器测试验证鉴权、参数和冲突响应，任务测试验证失败指标与停用开关。
