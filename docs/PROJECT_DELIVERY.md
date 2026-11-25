# 项目交付说明

更新日期：2026-11-18。源码基线为 `ad0204f`，本次收尾修改 CI 与文档，不改变业务实现。

## 当前交付范围

项目已实现登录认证、分片上传、渐进式 HLS 转码、发布审核、播放弹幕、社交互动和搜索同步。
可靠性机制包含 Outbox/Inbox、租约隔离、有限重试、故障消息归档与受控重放，以及无引用转码对象清理。
架构入口见 [模块与上传职责](architecture/shared-modules-and-upload.md)，演示入口见
[媒体治理手册](MEDIA_GOVERNANCE_RUNBOOK.md)，讲解材料见 [面试专题](interview/README.md)。

## 验收入口

| 层次 | 执行入口 | 验收边界 |
| --- | --- | --- |
| 后端质量 | `mvn --batch-mode --no-transfer-progress clean verify` | 单元、集成、API E2E 与覆盖率；需要 Docker，默认不启用媒体故障矩阵 |
| 转码故障 | `bash scripts/e2e/transcode-fault-matrix.sh` | Linux/WSL，真实 MySQL、MinIO、FFmpeg；视频模块及依赖，7 种故障场景 |
| 前端质量 | `npm --prefix bilibili-web ci` 后执行 `npm --prefix bilibili-web run quality` | ESLint、Vitest、TypeScript 与生产构建 |
| 模拟网关浏览器 | 在 `bilibili-web` 安装 `npx playwright install --with-deps chromium`，再执行 `npm run test:e2e` | 页面交互、401 刷新、分片恢复、弹幕重连与创作者统计；不证明真实后端链路 |
| 完整发布验证 | 在 Linux/WSL 执行 `pwsh scripts/verification/release-validation.ps1 -RunBrowserE2e -RunFaultDrill` | 包含需要 FFmpeg 的后端转码故障矩阵、真实 Compose、浏览器、依赖故障演练与写链路 SLO |

完整发布验证会启动服务、产生测试数据并执行清理，须按 [README](../README.md) 准备工具与依赖。
若固定 MinIO 测试镜像无法拉取，在同一 Linux/WSL Shell 中先
`source scripts/e2e/prepare-minio-test-image.sh`，再启动 Maven 或 PowerShell 验证命令。
隔离测试的 Compose 覆盖文件与 Testcontainers 共用该镜像变量。普通 Compose 启动仍使用默认镜像。

## V12 互动计数升级

迁移前停掉旧版社交服务和负责举报处置的视频服务，避免旧实例在重算期间写业务事实或回放 Redis 增量。
在备份数据库后，由新版实例应用 Flyway V12，再恢复写流量。V12 根据有效点赞、收藏关系及可见评论重算，
保留播放量和弹幕量。旧的 Redis 社交增量已经体现在事实表中，升级后无需再回放。
回退到旧版前须停写并清理该版本遗留的社交待同步集合及 `video:stats:*` 中的 `likeCount`、`collectCount`、`commentCount` 字段，
保留同一个 Hash 内的播放和弹幕字段。

## CI 收尾

- Push/PR 的前端质量和模拟网关浏览器测试统一由 [ci.yml](../.github/workflows/ci.yml) 执行，删除重复工作流。
- 定时/手动发布验证等待 backend、frontend、compose 三项门禁成功；其独立运行器也准备 MinIO 测试镜像。
- 保留浏览器 trace、覆盖率、转码故障与发布验证附件。远端运行结果以 GitHub Actions 为准。
- 若仓库分支保护曾将旧 `browser` job 设为必需检查，管理员需将其替换为现有 `Frontend quality`；本次未检查或修改远端保护规则。

## 证据与局限

- 2026-11-04 后端基线：129 项单元测试、40 项集成测试通过，包含 7 项真实媒体故障矩阵；见 [原始验收报告](reports/2026-11-04-transcode-fault-matrix.md)。这是历史结果，本次未重新执行后端全套。
- 本次收尾验证见 [2026-11-18 收尾报告](reports/2026-11-18-project-wrap-up.md)。
- 当前提交的完整 Compose 双 Worker/MQ 演练、真实浏览器和容量压测仍需独立执行；不能用模拟浏览器或隔离矩阵代替。
- 历史压力报告仅代表当时版本和负载；本项目不据此承诺生产容量、长期稳定性或生产上线就绪。
