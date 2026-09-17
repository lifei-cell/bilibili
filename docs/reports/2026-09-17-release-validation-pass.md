# P0 发布复验通过记录

> 验证日期：2026-09-17。验证在本机 Docker Desktop Linux Engine、当前工作区代码和 Compose 数据环境执行；这份记录不代表远端 CI 或生产环境已发布。

## 执行命令

```powershell
$env:DOCKER_HOST = 'npipe:////./pipe/dockerDesktopLinuxEngine'
pwsh -NoProfile -File scripts/verification/release-validation.ps1 -RunFaultDrill -Duration '2m'
```

最终报告目录：`loadtest/results/release-validation-20260917-185204/`。

## 门禁结果

最终 `release-validation.json` 的 `passed=true`，五个步骤全部通过：

| 步骤 | 结果 | 证据 |
| --- | --- | --- |
| CPU 配额归一化 | PASS | 1 核、4 核满载和 4 核半载样本校验通过 |
| 后端验证 | PASS | Maven 9 模块，Testcontainers 集成测试和 JaCoCo 门禁通过 |
| 真实 Compose E2E | PASS | 视频 `2044`；上传 853.62 ms，转码 2515.73 ms；播放、互动、弹幕阶段通过 |
| 故障演练 | PASS | RocketMQ、Redis、Elasticsearch 短时故障后链路恢复并完成对账 |
| 写链路 SLO | PASS | 2 分钟 k6；HTTP 错误率和业务错误率均为 0 |
| 资源门禁 | PASS | 资源违规数 0；视频服务 CPU 配额使用峰值 10.985%，内存 25.6% |
| RocketMQ 积压 | PASS | `cache-sync=8`、`danmu-persist=3`、`video-view=8`、`video-transcode=0`，均低于 1000 |
| 清理 | PASS | E2E 清理和 Compose 服务停止通过 |

写链路的 k6 指标如下：

| 链路 | P95 | 门槛 | 业务错误率 |
| --- | ---: | ---: | ---: |
| 播放 | 37.24172 ms | 500 ms | 0 |
| 弹幕 | 335.823816 ms | 1000 ms | 0 |
| 互动 | 394 ms | 1500 ms | 0 |

资源门禁按容器 CPU 配额归一化。RocketMQ broker 的 CPU 配额峰值为 96.51%，其 120% CPU 例外门槛通过；内存峰值为 54.61%，低于 85% 门槛。

## 本次修复

1. `UserApiIT` 的 Flyway 迁移断言更新为当前 V1–V6 六个成功迁移。
2. `SearchIndexMaintenanceService` 使用 `Query.multiGetQuery` 构造 ES 批量 ID 查询，避免 Spring Data Elasticsearch 6 将普通 ID 查询转换为空 `mget` 请求；单元测试增加 `getIdsWithRouting()` 回归断言。
3. RocketMQ broker 保留 768 MiB JVM 堆，将容器 `mem_limit` 提升到 1536 MiB，为堆外内存和故障恢复预留空间，保持 85% 内存门禁不变。

## 证据边界

- 报告 JSON、k6 汇总和资源采样保存在上述本地 `loadtest/results` 目录；该目录由仓库忽略规则管理，不作为源码提交物。
- 本次 Docker Engine 通过 `DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine` 访问；复验结束后 Compose 服务已停止。
- 远端 CI、镜像推送和生产部署仍需在对应环境单独执行。
