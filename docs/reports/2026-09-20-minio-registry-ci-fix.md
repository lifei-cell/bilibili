# MinIO 镜像源导致远端后端 CI 失败修复

> 日期：2026-09-20。范围仅为远端 CI 镜像可拉取性与对应本地回归，不替代发布验证或生产部署证据。

## 现象与根因

- GitHub Actions `CI #28` 的 `Backend verify` 失败，运行地址：<https://github.com/lifei-cell/bilibili/actions/runs/35491084692>。
- `HlsAttemptCleanupIT` 拉取 `minio/minio:RELEASE.2024-12-18T13-15-44Z` 时返回 HTTP 404；本机已有旧镜像缓存，因此此前本地门禁没有暴露问题。
- 失败属于构建环境依赖不可重建，不是 HLS 清理业务断言失败。

## 修复

- Compose 与 `HlsAttemptCleanupIT` 统一使用 `quay.io/minio/minio:RELEASE.2024-12-13T22-19-12Z`。
- 开发手册同步镜像来源，避免文档、E2E 与 Testcontainers 继续漂移。
- 该固定标签已通过远端 registry manifest 查询，包含 linux/amd64 与 linux/arm64 镜像。

## 本地验证

- 从 Quay 实际拉取新镜像后，`HlsAttemptCleanupIT`：1 项测试，0 失败、0 错误、0 跳过。
- `mvn --batch-mode --no-transfer-progress clean verify`：9 个模块全部成功；35 份测试报告、94 项测试，0 失败、0 错误、0 跳过。
- `docker compose -f docker-compose.yml -f docker-compose.service.yml -f docker-compose.e2e.yml config --quiet` 作为 Compose 合并模型门禁。

## 证据边界

- 上述回归在 Windows Docker Desktop Linux Engine 完成；远端是否恢复必须以修复提交触发的新 GitHub Actions 运行结果为准。
- 这次修复只恢复依赖可拉取性，不说明发布验证、目标容量或生产部署已经完成。
