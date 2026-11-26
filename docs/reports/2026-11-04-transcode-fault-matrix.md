# 转码故障矩阵验证报告

验证日期：2026-11-04。对应本报告同批提交的源码。

## 结果

在 WSL Ubuntu 中执行全后端 `clean verify`，14 个 Maven 模块全部成功，耗时 5 分 15 秒。
执行用 Java 源文件与原工作区逐字节核对一致。

| 检查 | 执行数 | 失败 / 错误 / 跳过 |
| --- | ---: | --- |
| Surefire 单元测试 | 129 | 0 / 0 / 0 |
| Failsafe 集成测试 | 40 | 0 / 0 / 0 |
| 其中：转码故障矩阵 | 7 | 0 / 0 / 0 |
| 其中：进程回收单元测试 | 3 | 0 / 0 / 0 |

全部 JaCoCo 覆盖率门禁通过；两个新增 Shell 脚本通过 `bash -n`，提交差异通过 `git diff --check`。
矩阵的 7 项包含三类故障在首档发布前后的 6 种组合，以及旧 Worker 迟到完成的独立场景。

## 已验证的行为

- FFmpeg 真实非零退出、SIGSTOP 挂起、媒体对象部分写入后 master PUT 返回 HTTP 503，均命中指定阶段。
- 首档前失败在两次尝试后进入失败终态；首档后在两次补偿耗尽后保留可播放状态，停止自动领取。
- 每次首档后失败均保持任务表和视频表的播放引用，并用 FFmpeg 拉取、解码真实 HLS。
- 故障移除并通过已有重置/重排队语义恢复后，360P 和 720P 均完成发布。
- 新代次持有有效租约时拒绝旧结果；新代次完成后恢复旧 Worker，当前播放地址仍不变。
- 保留期内不删除尝试对象；到期清理无引用对象，保留播放或封面引用的包；解除最后一个封面引用后旧包可清理。
- 挂起超时后包装器与编码子进程均已退出，临时工作目录为空；线程中断也会回收外部进程。

验证中保留了现有封面策略：转码更新不会覆盖视频已经选择的封面，因此旧封面所引用的包不能当作孤儿删除。
两个 MinIO 集成测试均等待 `/minio/health/ready`，避免将“端口已开放”误当作 S3 服务就绪。

## 环境与复现

- WSL Ubuntu，OpenJDK 21.0.12.1，Maven 3.9.12，Docker Engine 29.9.1。
- FFmpeg 8.0.1，CPU/libx264；2 秒、1280×720、12 FPS 合成源视频。
- MySQL 8.4；MinIO 由固定源码提交 `68b004a48f418ed9c4363b9faaf61a90d2f6f502` 构建。
- 本次 MinIO 镜像 ID：`sha256:b5e3c541c24bd4fe9ad03745da5713d4e82ad6f54149423cffadf561e397b199`。

```bash
MINIO_TEST_IMAGE=bilibili-test-minio:68b004a48f41 \
  mvn --batch-mode --no-transfer-progress clean verify -Dtranscode.fault.matrix=true
```

日常入口及镜像准备见 [转码故障矩阵运行手册](../runbooks/transcode-fault-matrix.md)。
原始日志、环境、汇总 JSON 和各模块 XML 报告位于本地忽略目录
`loadtest/results/transcode-fault-matrix-20261104-final/`，未提交原始运行数据。

## 证据边界

本轮为真实 MySQL/MinIO/FFmpeg 的隔离集成验证，直接调用生产消费者和状态转换逻辑；
没有重跑整套 Compose 双 Worker/MQ 演练、浏览器流程或容量压测，也不外推 GPU、生产容量和长期稳定性。
对象保留期通过测试 cutoff 推进，重试资格和租约到期通过隔离数据库推进；生产保留期与重试策略未被修改。
GitHub Actions 已配置执行矩阵并上传报告，本地结果不代表远端 CI 已运行通过。
