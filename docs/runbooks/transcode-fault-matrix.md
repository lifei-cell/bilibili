# 转码故障矩阵

本地验证记录见 [2026-11-04 验证报告](../reports/2026-11-04-transcode-fault-matrix.md)。

## 范围与入口

`TranscodeFaultMatrixIT` 使用隔离的 MySQL、MinIO 容器和本机真实 FFmpeg/ffprobe，
直接调用生产 `VideoTranscodeConsumer`，复用生产 Mapper、事务结果提交、租约与对象清理。
它补充 `scripts/e2e/p1-reliability-drill.ps1 -CrashAfterPlayable` 的双 Worker 崩溃接管演练。
矩阵不启动 Gateway/RocketMQ，因此不替代该 Compose 演练或真实浏览器发布验证。

Linux/WSL 需要 JDK 21+、Maven 3.9+、Docker、FFmpeg（含 libx264）和 ffprobe：

```bash
bash scripts/e2e/transcode-fault-matrix.sh
```

脚本执行视频服务及依赖模块的完整 `verify`，开启 `-Dtranscode.fault.matrix=true`。
普通 `verify` 不要求本机安装 FFmpeg，会跳过这个显式启用的矩阵；GitHub Actions 的
后端门禁安装 FFmpeg 并开启矩阵。Docker 不可用、工具缺失或故障断言失败均不能算通过。

原始 Maven 日志、环境和矩阵 Failsafe XML/TXT 保存在
`loadtest/results/transcode-fault-matrix-*/`。CI 上传 `transcode-fault-matrix` 附件。

## 故障与断言

| 场景 | 注入位置 | 必须满足 |
| --- | --- | --- |
| FFmpeg 非零退出 × 2 阶段 | 测试包装器在目标 HLS 批次调用真实 FFmpeg，指定不存在的编码器 | 错误入库；首档前有限重试后失败，首档后补偿耗尽但仍可播放 |
| FFmpeg 挂起 × 2 阶段 | 启动编码进程后发送 SIGSTOP | 8 秒执行超时；包装器和子进程均退出；临时目录清空；任务释放租约并收敛 |
| MinIO 写入失败 × 2 阶段 | 真实 HTTP 代理允许媒体对象 PUT，拒绝目标 master PUT 并返回 503 | 明确观测已成功的媒体写入与失败请求；不提交不完整包；旧 master 保持可解码 |
| 旧 Worker 迟到 | 首档发布后暂停旧执行流，过期租约并让新代次完成，再恢复旧执行流 | 新代次播放引用不变；旧结果、旧失败回写均被拒绝 |

两个阶段分别为首档 360P 发布前与首档发布后的 720P 处理阶段。每个故障场景都验证：

1. 实际触发了指定故障，错误类型与预期一致，而不是因其他依赖异常“碰巧失败”。
2. 首档前最多两次尝试；首档后最多两次补偿。耗尽后不再可调度，也不能再次领取。
3. 每次首档后失败都核对任务表和视频表播放地址，并用 FFmpeg 拉取、解码真实 HLS。
4. 恢复依赖后通过已有任务重置/管理员补偿重排队语义恢复，最终产物包含 360P 与 720P。
5. 清理保留期内不删对象；到期不删仍被播放或封面引用的旧尝试包。切换成功后，
   旧封面仍在引用的包继续保留；模拟选择新封面、解除最后一个引用后，旧尝试被清理，
   当前包仍能解码。用测试传入的 cutoff 模拟到期，不修改生产保留期，也不等待数天。

故障开关、HTTP 代理和暂停点都在测试夹具中，生产应用没有故障接口或开关。
重试时间和租约到期时间在隔离数据库内推进；实际状态转换、重试计数和 fencing 条件
仍由生产 SQL 执行。结论仅覆盖 CPU/libx264、小视频和这些故障窗口，不代表 GPU、
生产容量、整套 MQ 重投递或长期稳定性。

## MinIO 镜像准备

先复用或拉取固定镜像 `quay.io/minio/minio:RELEASE.2024-12-13T22-19-12Z`。
若不可用，脚本从同一版本的官方源码提交
`68b004a48f418ed9c4363b9faaf61a90d2f6f502` 构建
`bilibili-test-minio:68b004a48f41`（`docker/test/minio.Dockerfile`），保持独立镜像名。
构建需要访问 GitHub、Go 模块源和 Go 基础镜像；Linux 构建阶段使用 host 网络并透传
已有 HTTP(S) 代理，以兼容 WSL 的本机代理。不会替换演示栈镜像或数据卷。
来源许可保留在测试镜像 `/LICENSE` 中。可通过 `MINIO_TEST_IMAGE` 指定预先准备的测试镜像。

单独准备后手动运行：

```bash
source scripts/e2e/prepare-minio-test-image.sh
mvn --batch-mode --no-transfer-progress -pl bilibili-video-service -am verify \
  -Dtranscode.fault.matrix=true
```

## 进程清理

`MediaProcessRunner` 统一处理 FFmpeg 和 ffprobe 的有限等待。超时或中断时先终止子进程，
给包装器机会回收子进程，再终止并等待主进程，最后才由 Worker 删除工作目录。
`MediaProcessRunnerTest` 在不依赖 FFmpeg/Docker 的情况下覆盖超时、中断与非零退出码。
