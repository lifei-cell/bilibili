# 可靠性闭环 Runbook

## 保障范围

核心链路为：上传分片 → MinIO 合并 → 转码任务表 → RocketMQ → FFmpeg → 发布审核 → 播放 → CDC → Elasticsearch → 评论、点赞、弹幕。

- `video_transcode_task` 是转码专用 Outbox；通用 `reliable_event_outbox` 承载播放量、弹幕持久化和 CDC 事件。
- `mq_consumed_message` 使用 `consumer_group + topic + message_key` 唯一键和 token/generation 租约完成消费 Inbox 幂等；完成和失败回写同时校验领取者及有效期。
- Outbox 采用至少一次投递；发送成功但状态未落库时允许重复发送，消费端必须保持业务幂等。
- 消费失败先进行应用内有限重试，再写应用 DLQ 并抛回 RocketMQ。短时依赖故障恢复后由 Broker 重投；成功后 DLQ 状态改为 `RESOLVED`。
- 弹幕以业务 ID `insert ignore`，播放量以 `videoId + requestId` Redis Lua 去重，CDC 以 Binlog 文件、偏移和行序号生成稳定事件 ID。

## Inbox 租约边界

业务每次尝试在独立数据库事务中先锁定当前 Inbox 行，再执行业务操作与成功标记；接管等待这笔事务提交或回滚。领取者已更换或租约已过期时，回调不执行；业务执行后租约过期时，数据库写入全部回滚，消息抛回 Broker。最终失败标记与 DLQ 写入也在同一受保护事务中完成。旧消费者不再重试业务或修改 DLQ。

只有 `SUCCEEDED` 的重复消息可以直接确认。`PROCESSING` 的竞争投递必须抛回 Broker，防止唯一的重投在原消费者崩溃前被误确认。指标 `bilibili_mq_consume_total` 的 `result=busy` 和 `result=lease_lost` 分别表示竞争消费和租约失效。

回调中的数据库写入必须加入当前事务。弹幕可靠消费采用同线程写入，避免后台队列越过 Inbox 的事务边界；非事务调用仍保留原批量队列。Redis 和 Elasticsearch 不参与数据库事务，继续依赖稳定业务键去重及现有回源同步；这不是跨系统原子提交。数据库连接丢失后的外部在途请求也不能仅凭 Inbox 行锁撤销。

`reliability.mq.inbox-lease-seconds` 默认 60 秒。超过期限的数据库业务不会提交，需要按实际耗时调整配置；行锁会占用一个数据库连接，应关注连接池等待和消息积压。FFmpeg 使用独立转码租约，不走该执行器。

升级包含 Flyway V9。部署时先停止并等待旧版消费者退出，再迁移和启动新版；旧版代码不校验 token，不能与新版消费者混跑。过期旧记录可由新版接管，已成功记录继续去重。

定向验收：

```powershell
mvn -s .mvn/settings.xml --batch-mode --no-transfer-progress -pl bilibili-video-service -am '-Dit.test=MessageInboxLeaseMySqlIT' '-Dfailsafe.failIfNoSpecifiedTests=false' verify
```

## 自动回归

```powershell
./scripts/e2e/reliability-e2e.ps1
./scripts/e2e/reliability-e2e.ps1 -RunFaultDrill
```

`docker-compose.e2e.yml` 只缩短调度和统计刷盘周期，不改变可靠性语义。CI 每晚自动执行完整故障演练，也可通过 `workflow_dispatch` 手动触发。

验收条件：

1. 上传、转码、发布、播放、搜索和三类互动均成功。
2. 弹幕最终只落一条，Outbox 最终为 `PUBLISHED`，Inbox 最终为 `SUCCEEDED`。
3. RocketMQ 停止期间事件保留在 MySQL，恢复后自动投递。
4. Redis 停止期间播放 DB 主路径可用；恢复后 Broker 重投并完成计数。
5. Elasticsearch 恢复后全量重建成功，CDC 对账 `consistent=true`。

## 对账和重建

```bash
curl -H "X-Admin-Token: $OPERATIONS_ADMIN_TOKEN" \
  http://localhost:8080/api/admin/reliability/cdc/reconcile

curl -X POST -H "X-Admin-Token: $OPERATIONS_ADMIN_TOKEN" \
  http://localhost:8080/api/admin/reliability/es/rebuild
```

搜索与 CDC 写入统一走 `video_search` Alias。首次启动时，若存在旧物理索引 `video_index`，Alias 指向旧索引；否则创建 `video_index_vinitial`。重建按视频 ID 每页 200 条读取 MySQL，写入独立 `video_index_v<UUID>`；旧 Alias 持续承接搜索。最终追平时暂缓本实例 CDC 索引写入，刷新影子索引、清理孤儿文档，并以 MySQL 已发布且未删除的视频为源对账；一致后使用 ES 单次 Alias 更新移除旧索引并加入新索引。失败不切换 Alias，旧索引不会自动删除。对账返回缺失、孤儿和字段不一致 ID，每类最多 100 个样本。

回退时取上次重建响应的 `previousIndex`，调用下面接口。服务先按 MySQL 分页追平目标旧索引、复核后再切换 Alias，防止旧索引在切换后停止接收 CDC 导致数据回退。只接受保留的 `video_index`、`video_index_vinitial` 或 `video_index_v<UUID>`；旧索引由运维在回滚窗口结束后手工清理。

```bash
curl -X POST -H "X-Admin-Token: $OPERATIONS_ADMIN_TOKEN" \
  "http://localhost:8080/api/admin/reliability/es/rollback?targetIndex=video_index"
```

当前暂缓 CDC 的进程内读写锁由 MySQL 命名锁扩展到跨 Canal 实例；重建最终追平、Alias 切换和视频 CDC 写入共用同一命名锁。切换最多等锁 30 秒，锁等待与持有时间由 `bilibili.index.lock.wait`、`bilibili.index.lock.hold` 记录，可按 `operation=cdc|cutover` 在 Canal 实例的 Actuator 指标接口查看。本地量化演练运行 `scripts/e2e/p1-reliability-drill.ps1 -MeasureIndexCutover`；采样、口径和结果见 `docs/reports/2026-09-18-index-cutover-lock-cdc-backlog.md`。该结果不构成目标规模的容量 SLO。搜索结果 Redis 缓存最长保留 30 秒，切换后可能短暂返回旧结果。容器内 ES 联调以发布验收报告为准。

## 人工处置

| 现象 | 查询 | 处置 |
| --- | --- | --- |
| Outbox 长时间未发布 | `select owner,status,count(*) from reliable_event_outbox group by owner,status` | 检查 Broker、生产者日志和 `bilibili_outbox_publish_total`；恢复依赖后等待重试 |
| Inbox 持续失败 | `select topic,status,count(*) from mq_consumed_message group by topic,status` | 检查依赖；应用 DLQ 可通过 `/api/admin/mq/failures` 查询和重放 |
| ES 与 MySQL 不一致 | 调用 CDC 对账接口 | 先确认 Canal/MQ，再执行全量重建并复查 |
| Redis 恢复后统计未收敛 | 检查 `video:stats:*` 和 `video:stats:*:pending` | 等待统计刷盘任务；仍异常时以业务表和 Inbox/DLQ 逐条核对 |

故障演练只允许停止明确的 Compose 服务，不删除数据卷。生产环境执行前必须确认流量摘除、管理员令牌、备份和回滚窗口。
