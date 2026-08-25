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

## WebSocket 长连接

该脚本以匿名观众连接演示视频，并按会话时长动态发送心跳（间隔最长 15 秒），不会写入弹幕数据。

```powershell
docker run --rm -i --add-host=host.docker.internal:host-gateway `
  -e VUS=100 -e SESSION_MS=45000 `
  -v "${PWD}/loadtest:/scripts" grafana/k6 run /scripts/ws-connections.js
```

## 观察项

压测窗口内同步记录网关、视频/搜索/弹幕服务的 CPU、内存、GC、线程与连接池；同时
观察 Redis 内存和慢日志、MySQL 慢查询/锁等待、RocketMQ 消费滞后、Elasticsearch
查询延迟，以及 `video_transcode_task` 的任务积压。上传和转码应使用独立数据集单独
执行，避免 FFmpeg 资源争用污染 HTTP 与 WebSocket 基线。
