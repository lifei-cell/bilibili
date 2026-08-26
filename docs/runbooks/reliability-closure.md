# 可靠性闭环 Runbook

## 保障范围

核心链路为：上传分片 → MinIO 合并 → 转码任务表 → RocketMQ → FFmpeg → 发布审核 → 播放 → CDC → Elasticsearch → 评论、点赞、弹幕。

- `video_transcode_task` 是转码专用 Outbox；通用 `reliable_event_outbox` 承载播放量、弹幕持久化和 CDC 事件。
- `mq_consumed_message` 使用 `consumer_group + topic + message_key` 唯一键和租约完成消费 Inbox 幂等。
- Outbox 采用至少一次投递；发送成功但状态未落库时允许重复发送，消费端必须保持业务幂等。
- 消费失败先进行应用内有限重试，再写应用 DLQ 并抛回 RocketMQ。短时依赖故障恢复后由 Broker 重投；成功后 DLQ 状态改为 `RESOLVED`。
- 弹幕以业务 ID `insert ignore`，播放量以 `videoId + requestId` Redis Lua 去重，CDC 以 Binlog 文件、偏移和行序号生成稳定事件 ID。

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

对账以 MySQL 已发布且未删除的视频为源，核对 ES 的缺失、孤儿和字段不一致 ID，单类最多返回 100 个样本。全量重建会清空并分批写回 `video_index`，适合故障恢复或维护窗口；重建后自动执行一次对账，未收敛则接口失败。大规模生产索引应进一步升级为影子索引加 Alias 原子切换。

## 人工处置

| 现象 | 查询 | 处置 |
| --- | --- | --- |
| Outbox 长时间未发布 | `select owner,status,count(*) from reliable_event_outbox group by owner,status` | 检查 Broker、生产者日志和 `bilibili_outbox_publish_total`；恢复依赖后等待重试 |
| Inbox 持续失败 | `select topic,status,count(*) from mq_consumed_message group by topic,status` | 检查依赖；应用 DLQ 可通过 `/api/admin/mq/failures` 查询和重放 |
| ES 与 MySQL 不一致 | 调用 CDC 对账接口 | 先确认 Canal/MQ，再执行全量重建并复查 |
| Redis 恢复后统计未收敛 | 检查 `video:stats:*` 和 `video:stats:*:pending` | 等待统计刷盘任务；仍异常时以业务表和 Inbox/DLQ 逐条核对 |

故障演练只允许停止明确的 Compose 服务，不删除数据卷。生产环境执行前必须确认流量摘除、管理员令牌、备份和回滚窗口。
