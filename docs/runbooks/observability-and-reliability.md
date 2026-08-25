# 可观测与可靠性运行手册

## 1. 入口与数据流

- Prometheus：`http://localhost:9090`，每 15 秒抓取七个 Java 服务和 RocketMQ Exporter。
- Grafana：`http://localhost:3000`，已预置 Prometheus、Loki、Tempo 数据源与 `Bilibili Service Overview` Dashboard。
- Alertmanager：`http://localhost:9093`。仓库默认只聚合告警，生产部署需在 `alertmanager.yml` 增加企业微信、邮件或 PagerDuty Receiver。
- HTTP/消息链路：`X-Request-Id` 用于请求关联，`X-Trace-Id`/日志 `traceId` 用于跨服务检索；Micrometer Tracing 的 Span 上报 Tempo。

## 2. 核心告警处置

| 告警 | 第一检查项 | 常见处置 |
| --- | --- | --- |
| `BilibiliServiceDown` | Compose 状态、容器日志、`/actuator/health` | 检查依赖健康和资源 OOM，再决定重启 |
| `CriticalApiHighErrorRate` | Grafana API 面板与同 TraceId 日志 | 按错误类型定位依赖或代码，不盲目扩大重试 |
| `CriticalApiP95Slow` | JVM、数据库连接池、Redis/ES 延迟 | 优先消除慢依赖或热点，再评估扩容 |
| `ApplicationDlqPending` | 查询 `/api/admin/mq/failures` | 修复根因后按消息逐条重放并观察指标 |
| `RocketMqConsumerLagHigh` | Topic、consumer group、消费错误日志 | 检查消费者存活、下游延迟和 DLQ |
| `JvmHeapPressure` | GC、堆使用、容器内存 | 排查泄漏或大对象，禁止只提高 Xmx 掩盖问题 |

## 3. DLQ 查询与重放

查询只返回 `FAILED` 或 `REPLAYED` 状态，单页最多 100 条：

```bash
curl -H "X-Admin-Token: $OPERATIONS_ADMIN_TOKEN" \
  "http://localhost:8080/api/admin/mq/failures?topic=cache-sync&status=FAILED&page=1&size=20"
```

重放前必须确认导致消费失败的 MySQL、Redis、Elasticsearch 或业务数据问题已经恢复。重放使用数据库中记录的原 Topic 和载荷类型，并在同步发送成功后更新为 `REPLAYED`：

```bash
curl -X POST -H "X-Admin-Token: $OPERATIONS_ADMIN_TOKEN" \
  "http://localhost:8080/api/admin/mq/failures/{id}/replay"
```

禁止批量无脑重放。消费端保留业务幂等约束；同一失败指纹重复出现时累计尝试次数，不无限新增记录。

## 4. 超时和重试边界

- Gateway：连接超时 3 秒、响应超时 5 秒；仅 GET 的 502/503/504 最多重试 2 次并指数退避。
- Redis：连接和命令默认超时 2 秒；数据库连接获取默认超时 3 秒。
- RocketMQ Producer：发送超时 3 秒、失败重试 2 次。
- 消费者：默认最多执行 3 次，100ms 起步退避；耗尽后持久化到应用 DLQ并确认原消息，避免无限占用消费线程。
- 视频转码：继续使用 `video_transcode_task` 的持久化状态、下次重试时间和 FFmpeg 总超时，不进入短周期通用重试。

## 5. 资源配置

Java 容器默认限制为 1 CPU、768MB，JVM 最大使用容器内存的 70%，OOM 时立即退出并由 Compose 重启。生产环境应根据压测数据调整 Compose 限额，而不是在容器限额之外硬编码 `-Xmx`。

启动后检查：

```bash
docker compose -p bilibili -f docker-compose.yml -f docker-compose.service.yml ps
docker stats --no-stream
curl http://localhost:9090/api/v1/rules
curl http://localhost:8080/actuator/prometheus
```
