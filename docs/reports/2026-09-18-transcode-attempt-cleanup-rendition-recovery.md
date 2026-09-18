# 转码失效产物清理与高档位补偿

实施日期：2026-09-18。范围：`bilibili-video-service`、Flyway V7、P1 故障演练诊断。

## 业务约束与实现

- 首档 HLS 一旦发布，转码任务仍以 `status=3` 表示可播放；高档位另用 `rendition_status` 表示生成中、完成、待补偿、补偿失败和已派发。客户端状态接口返回补偿次数、下次重试时间和错误原因。
- 高档位失败或 Worker 在首档发布后失租，调度器以数据库租约、代次和令牌重新领取，最多自动补偿 3 次。重试会重新编码整段源视频，但不再次发布低清地址；完整 HLS 与视频播放地址在同一个 MySQL 事务中切换。旧 Worker 的迟到写入被代次条件拒绝，重试失败仍保留原低清播放地址。
- 自动补偿耗尽后状态为 `FAILED`，管理员可携带 `X-Admin-Token` 调用 `POST /api/admin/video/transcode/{taskId}/retry-renditions` 重新排队；只接受仍有可播放地址的补偿失败任务。该接口不改变当前播放地址。
- `HlsAttemptCleanupTask` 每 5 分钟分批检查历史任务下的 `attempt-*` 对象。默认保留 168 小时；删除前要求该尝试没有有效租约、最近对象已过保留期，且任务和视频表均未引用其播放、封面或清晰度 URL。每轮最多检查 10 个任务，每个文件标识最多枚举 10,000 个对象；超限时跳过并记录日志。删除中断后下次扫描可重试。
- Flyway V7 回填历史成功任务的高档位状态：仍持有令牌的视为生成中，带错误且有播放地址的视为待补偿，其余视为完成。首次上线应按既有发布流程停止旧 Worker、执行迁移、再启动新版本。

配置：`VIDEO_TRANSCODE_CLEANUP_ENABLED`、`VIDEO_TRANSCODE_CLEANUP_RETENTION_HOURS`、`VIDEO_TRANSCODE_CLEANUP_INTERVAL`；`VIDEO_TRANSCODE_MAX_RETRIES` 同时限制首次转码重试和高档位自动补偿。

## 验证记录

- 定向 H2/Mock 测试：补偿派发、重复消息拒绝、有限重试耗尽、旧代次无法提交、低清地址保持、状态接口，以及新旧对象清理条件通过。
- 真实 MySQL Testcontainers：V1–V7 迁移、首档已发布后租约过期重新领取、管理员重新排队、原低清地址保持和旧代次写入拒绝通过。
- 真实 MinIO Testcontainers：孤立 `attempt-*` 对象删除，仍被引用的 HLS 对象保留。
- 九模块 `mvn -s .mvn/settings.xml --batch-mode --no-transfer-progress clean verify`：2026-09-18 本地 Docker Engine 上通过，全部 9 模块 SUCCESS；Surefire/Failsafe 汇总 93 个测试，失败 0、错误 0、跳过 0。
- P1 Docker 双 Worker/双 Canal 演练：首次运行 `p1-reliability-drill-20260918-143227` 因第二 Worker 未在 240 秒内完成接管而未通过，清理成功；脚本现保留失败时任务快照和存活 Worker 日志。复跑 `p1-reliability-drill-20260918-144225` 的 `passed=true`、`cleanupPassed=true`，代次 1 崩溃后代次 2 产出可播放 HLS，多 Canal Alias 与 CDC 对账通过。两次结果分别保留，不将一次通过解释为稳定性证明。

## 边界

本地集成测试直接验证补偿状态和对象删除；P1 演练验证真实容器崩溃接管，没有在真实容器中注入“低清已发布后高档位超时”的完整重试旅程。高档位补偿重新编码低清档，增加计算成本，但在完整结果提交前不会替换已发布地址。源视频若已按生命周期清理，补偿会失败并进入可查询失败态，需要先恢复源文件再由管理员重新排队。以上均为本地证据，不代表远端 CI 或生产环境。
