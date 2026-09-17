# CPU 配额门禁统一与发布复跑记录

## 改动

- 新增 `scripts/loadtest/resource-normalization.ps1`，从 Docker `NanoCpus`、`CpuQuota/CpuPeriod`、`CpuCount` 或 `CpusetCpus` 解析容器实际 CPU 配额；缺少配额时直接失败，避免把宿主机百分比误当成单核百分比。
- `docker stats` 的 `CPUPerc` 作为原始值保留，门禁使用 `rawCpuPercent / quotaCores` 得到 `cpuQuotaPercent`。资源峰值同时记录两种口径和配额核数。
- 写链路固定采集视频服务，并通过 Compose service label 发现运行中的 `bilibili-transcode-worker`，报告按 `video-service` 与 `transcode-worker` 角色分别汇总。
- `loadtest/write-slo.json` 将门槛字段统一为 `maxCpuQuotaPercent`；RocketMQ Broker 的 120% 例外也按配额口径解释。
- 发布入口先执行 CPU 归一化校验，并将结果写入 `release-validation.json`。

## 算法校验

命令：

```powershell
pwsh -NoProfile -File scripts/loadtest/test-resource-normalization.ps1
```

结果：

- 1 核：原始 100% ÷ 1 = 配额使用率 100%，通过。
- 4 核：原始 400% ÷ 4 = 配额使用率 100%，通过。
- 4 核半载：原始 200% ÷ 4 = 配额使用率 50%，通过。

## 完整发布复跑

命令：

```powershell
pwsh -NoProfile -File scripts/verification/release-validation.ps1 -RunFaultDrill
```

最新报告：
`loadtest/results/release-validation-20260917-164106/release-validation.json`

报告中的 `resourceNormalization` 已为 `PASSED`，但完整发布仍为 `passed=false`：

- Testcontainers 无法连接 `npipe:////./pipe/docker_engine`，后端集成测试失败；
- Compose 也无法访问 `npipe:////./pipe/dockerDesktopLinuxEngine`，因此 E2E、Worker 采样、k6 和资源门禁尚未执行。

当前不能把环境阻塞报告改写成 `passed=true`。恢复 Docker Desktop 命名管道访问后，应在同一提交上重新执行上述命令，并确认报告的所有步骤、视频服务与转码 Worker 角色采样、MQ 积压和资源门禁均通过后再关闭 P0。
