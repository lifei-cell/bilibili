# 2026-09-11 发布门禁复跑记录

> 历史状态：本报告记录的是 2026-09-11 的失败候选；后续通过结果见 [2026-09-17 发布复验](./2026-09-17-release-validation-pass.md)，不得用本报告替代当前提交的重新验证。

## 结论

发布门禁未关闭，未提交、未推送、未触发远端 CI。最终报告：
`loadtest/results/release-validation-20260911-143313/release-validation.json`（本地忽略目录）。

## 最终候选复跑

命令：

```powershell
pwsh -NoProfile -File scripts/verification/release-validation.ps1 -RunFaultDrill
```

结果：

- 后端 `mvn verify`：通过，9 个模块、72 个单元测试、1 个 Testcontainers 集成测试及 JaCoCo 门禁通过。
- 前端 `npm run quality`：通过，包含 ESLint、4 个 Vitest 测试、覆盖率、TypeScript 与生产构建。
- Compose E2E：通过；上传 1221.63 ms，转码 2592.71 ms；上传、转码、发布、播放、搜索、互动与清理均完成。
- 故障演练：通过；覆盖 RocketMQ/Outbox、Redis/Inbox、Elasticsearch 重建与对账恢复。
- k6 写链路：通过；HTTP 与业务错误率均为 0，播放 P95 434.53 ms、弹幕 P95 360.42 ms、互动 P95 492 ms。
- RocketMQ 积压：已采集且未超限，峰值为 8。
- 资源门禁：失败；`bilibili-video-service` CPU 峰值 424.39%，超过当前 85% 门槛。内存与其他容器均未超限。
- 清理：通过；临时视频、关联数据和容器均已清理/停止。

## 本轮修正

- 发布验证、E2E、写入 SLO 与 CI 证据上传脚本补齐并做 Linux/Windows 路径兼容。
- 本地 E2E HTTP 请求统一使用 `curl --noproxy '*'`，消除 PowerShell HTTP 栈每次约 21 秒的前置延迟；上传阶段由约 42.8 秒降至 1.22 秒。
- 上传会话创建后立即写入清理上下文，确保上传中途失败也能删除数据库记录与 MinIO 对象。
- CI 证据上传改为 `always()`，成功和失败运行均保留发布报告。

## 未关闭项

应先确认视频服务 424.39% CPU 是 FFmpeg 多核口径、历史任务干扰还是资源限制未生效，再决定按容器核数归一化指标、补齐 CPU quota，或优化转码并发。不得仅为通过门禁直接放宽阈值。修正后需重新执行完整发布验证；只有 `passed=true` 才可提交、推送并以同一提交 SHA 的手工 CI 运行和 artifact 作为交付证据。
