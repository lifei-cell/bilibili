# P1 真实故障复验记录（2026-09-17）

## 验证范围

本次在 Docker Desktop Linux Engine 上使用真实 MySQL、RocketMQ、MinIO、FFmpeg、Elasticsearch、Canal 和网关完成两组演练：

1. 两个同消费组的转码 Worker 同时运行；任务被一个 Worker 领取后，冻结并强制终止领取者，验证另一副本接管租约并完成转码。
2. 两个 Canal 应用实例连接同一 Canal、MySQL、RocketMQ 和 Elasticsearch；并发触发索引重建，同时写入视频标题，验证跨实例互斥、Alias 原子切换和 CDC 最终对账。

演练入口：`scripts/e2e/p1-reliability-drill.ps1`。执行前需确认 Docker Engine 可用；脚本会生成 60 秒 720P 测试源、创建临时视频、保留 JSON 报告并在结束时清理业务数据和容器。

```powershell
$env:DOCKER_HOST = 'npipe:////./pipe/dockerDesktopLinuxEngine'
pwsh -NoProfile -File scripts/e2e/p1-reliability-drill.ps1
```

## 结果

报告：`loadtest/results/p1-reliability-drill-20260917-224319/p1-reliability-drill.json`

| 门禁 | 实际结果 | 证据 |
| --- | --- | --- |
| 双 Worker 抢占 | 通过 | 两个副本均启动；实际日志识别 `bilibili-bilibili-transcode-worker-1` 领取代次 1；该容器被强制终止后状态为 `exited`，`worker-2` 观察到代次 2 接管。 |
| 崩溃恢复产物 | 通过 | 任务状态从代次 1 恢复到代次 2，令牌清空，输出位于 `attempt-2-.../master.m3u8`，播放清单可读取。 |
| 多 Canal 连接 | 通过 | `bilibili-canal-service` 与 `bilibili-canal-service-2` 均记录 `Canal connector connected`。 |
| 并发索引重建 | 通过 | 两个实例并发请求返回一个 `200` 和一个预期的锁竞争 `500`；只有一个请求完成验证与切换。 |
| Alias 与 CDC | 通过 | `video_search` Alias 解析到 1 个物理索引，CDC 对账 `consistent=true`，更新后的标题可被搜索到。 |
| 清理 | 通过 | JSON 报告 `cleanupPassed=true`，脚本退出状态为 0。 |

跨实例协调由 MySQL 命名锁提供：重建最终追平和 Alias 切换与视频 CDC 写入共用同一连接绑定锁；进程内读写锁继续缩小单实例临界区。重建在影子索引填充阶段不持有全局锁，减少对 CDC 的阻塞。

## 证据边界与后续

以上是当前提交工作区在本机 Docker 环境的真实容器证据，不代表远端 CI、生产部署或多节点容量结论。后续仍需补：规模化多 Canal 压测、锁等待和 CDC 积压 SLO、孤立 `attempt-*` 对象清理，以及高档位转码超时补偿。
