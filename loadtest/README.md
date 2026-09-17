# 压测脚本

脚本使用 k6，默认从 Docker 容器访问本机网关，因此地址使用
`host.docker.internal`。压测前需启动完整 Compose 环境，并确认 `2001` 等演示
视频已发布。

如需使用 JMeter，直接打开 [`jmeter/bilibili-read.jmx`](jmeter/bilibili-read.jmx)；
命令行运行、参数和 WebSocket 配置见 [`jmeter/README.md`](jmeter/README.md)。

## 读链路基线

默认每秒运行 5 个混合读场景；每个场景请求视频列表、详情和搜索，共约 15 RPS。

```powershell
docker run --rm -i --add-host=host.docker.internal:host-gateway `
  -v "${PWD}/loadtest:/scripts" grafana/k6 run /scripts/read-baseline.js
```

阶梯压测示例：

```powershell
docker run --rm -i --add-host=host.docker.internal:host-gateway `
  -e RATE=20 -e DURATION=5m `
  -v "${PWD}/loadtest:/scripts" grafana/k6 run /scripts/read-baseline.js
```

每个 `RATE` 迭代包含 3 个请求，实际 HTTP 请求量约为 `RATE * 3 RPS`。
推荐按 `5 -> 20 -> 50 -> 100` 阶梯执行，每档 5 分钟；发生阈值失败、服务错误或
组件持续积压时停止加压。

正式验收前先用同一脚本执行 30 秒预热，确认 JVM、连接池和热点缓存已就绪，再运行 `RATE=100 DURATION=5m`。当前门槛为 HTTP/业务错误率均 `<0.5%`、整体 P95 `<1s`，列表、详情和搜索各自 P95 `<500ms`。

## WebSocket 长连接

该脚本以匿名观众连接演示视频，并按会话时长动态发送心跳（间隔最长 15 秒），不会写入弹幕数据。

```powershell
docker run --rm -i --add-host=host.docker.internal:host-gateway `
  -e VUS=100 -e SESSION_MS=45000 `
  -v "${PWD}/loadtest:/scripts" grafana/k6 run /scripts/ws-connections.js
```

## 写链路 SLO

写链路使用两段式验证，避免把 FFmpeg 重计算与高频 HTTP 写入混在同一个基线中：

1. Compose E2E 生成隔离视频，并校验上传、转码、播放、弹幕和互动的单次时延；
2. k6 对该临时视频并发执行播放、发弹幕、评论创建/删除和点赞/取消点赞，同时每 5 秒采集容器 CPU/内存与 `rocketmq_group_diff` 曲线。

推荐通过发布入口运行，它会在完成后清理所有临时数据：

```powershell
./scripts/verification/release-validation.ps1
```

单独执行写入压测时，必须传入专用、可在压测后自行清理数据的已发布视频：

```powershell
./scripts/loadtest/run-write-slo.ps1 -VideoId <isolatedVideoId>
```

默认持续 2 分钟：播放 10 RPS、弹幕 1 RPS、互动 1 RPS；写操作在 6 个演示账号间轮询，确保基线不会把产品级单用户限流误判为服务失败。各链路 SLO、资源阈值和 RocketMQ 最大积压在 [`write-slo.json`](./write-slo.json) 中集中维护。报告 JSON 和 Markdown 位于 `loadtest/results/`，该目录不提交版本库。

资源门槛默认是 CPU 配额使用率和内存使用率均为 85%。脚本读取每个容器的 `NanoCpus`（或 `CpuQuota/CpuPeriod`）作为实际 CPU 核数，把 Docker 原始 `CPUPerc` 除以该核数后再比较门槛；报告同时保留原始 CPU 百分比。视频服务和运行中的 `bilibili-transcode-worker` 会按角色分别采集。RocketMQ Broker 单独采用 120% 配额 CPU 上限，以适配消息组件的短时峰值；该例外与消费积压阈值共同生效，不能单独替代消息堆积门禁。

## 观察项

压测窗口内同步记录网关、视频/搜索/弹幕服务的 CPU、内存、GC、线程与连接池；同时
观察 Redis 内存和慢日志、MySQL 慢查询/锁等待、RocketMQ 消费滞后、Elasticsearch
查询延迟，以及 `video_transcode_task` 的任务积压。上传和转码应使用独立数据集单独
执行，避免 FFmpeg 资源争用污染 HTTP 与 WebSocket 基线。
