# 索引切换锁等待与 CDC 积压量化

日期：2026-09-18。本地 Docker Desktop Linux Engine，真实 MySQL、Canal、RocketMQ、Elasticsearch，两个 Canal 应用实例。入口：`pwsh -NoProfile -File scripts/e2e/p1-reliability-drill.ps1 -MeasureIndexCutover`，设置 `DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine`。

## 实现与口径

- MySQL 命名锁分别记录 `bilibili.index.lock.wait` 和 `bilibili.index.lock.hold` Timer，标签 `operation=cdc|cutover`。等待值是调用 `GET_LOCK` 的耗时，不含连接池取连接、ES 写入或完整请求耗时；Timer 在每个实例分别累计，失败领取也计入等待。
- 索引最终追平与 Alias 切换等待共享锁最多 30 秒。第二个并发重建即使随后获得锁，仍检查原 Alias 是否改变，不能覆盖先完成的切换。
- 视频与视频统计 CDC 消息仅用事件中的视频 ID 定位对象；持锁时从 MySQL 读取当前已发布状态，再写 ES。这样较早事件晚到不会把索引回退到旧标题。用户、关注、点赞缓存仍按现有事件处理。
- 演练在初始视频 CDC 落定后记录 Outbox/Inbox 基线，并发请求两个实例重建索引，然后在同一 MySQL 会话顺序提交 20 次视频标题更新。约每 500 毫秒读取一次该视频的 Outbox 与 Inbox，保存每次采样和峰值。`未捕获=20-(Outbox 新增)`；`未消费=Outbox 新增-Inbox 成功新增`；`Outbox 待发布` 是 `status != PUBLISHED` 的行数。采样间隔加上 Docker/MySQL 查询开销，因此峰值是观测到的下界。以上不等于 Canal 服务内部队列深度或 RocketMQ Broker 积压。

## 本地结果

成功报告：`loadtest/results/p1-reliability-drill-20260918-192738/p1-reliability-drill.json`，`passed=true`、`cleanupPassed=true`。

| 指标 | 实测 |
| --- | ---: |
| 源表更新 / Outbox 捕获 / Inbox 成功 | 20 / 20 / 20 |
| 观测到的未捕获峰值 | 11 条 |
| 观测到的已捕获未消费峰值 | 14 条 |
| 观测到的 Outbox 待发布峰值 | 9 条 |
| 源表更新结束到 Inbox 全部成功 | 2,659 ms |
| 8086 CDC 锁等待 | 14 次，累计 8,589.820 ms，单次最大 962.250 ms |
| 8087 CDC 锁等待 | 9 次，累计 6,890.810 ms，单次最大 945.060 ms |
| 8086 切换锁等待 | 2 次，累计 452.490 ms，单次最大 451.920 ms |
| 8087 切换锁等待 | 1 次，690.360 ms |

一个并发重建返回 200，另一个因 Alias 已改变返回 500；`video_search` 最终只指向一个索引，CDC 对账 `consistent=true`，第 20 次更新的标题可搜索。8086 的切换 Timer 包含演练开头的初始重建；其余值也是实例启动以来该次演练的累计值。以上是单次本地短突发测量，不代表生产容量、P95/P99、持续负载 SLO 或远端 CI。

九模块 `mvn -s .mvn/settings.xml --batch-mode --no-transfer-progress clean verify` 在同一 Docker Engine 上通过：94 个测试，失败 0、错误 0、跳过 0。`loadtest/results/index-cutover-full-verify.log` 为本地构建日志。

## 故障发现与修正

- 首轮测量报告未生成：失败诊断保存了可递归的 Docker 日志对象，JSON 序列化耗尽内存。脚本改为保存纯文本日志，失败仍落报告。
- `p1-reliability-drill-20260918-191359`：两个重建都返回 500；容器日志指出 CDC 持锁时切换采用 `GET_LOCK(..., 0)`，立即失败。改为切换最多等待 30 秒。
- `p1-reliability-drill-20260918-191851`：20 条更新在 2,014 ms 内消费完毕，最大未消费 17 条；切换成功但最终对账超时。原消费者直接用事件快照写 ES，晚到的旧事件可覆盖新标题。改为持锁读取 MySQL 当前状态后，复验对账通过。

验收口径：两实例连接、至少一次完整切换、Alias 只指向一个物理索引、20 条更新均捕获并成功消费、最终标题可搜索、CDC 对账一致且清理成功。下一阶段如要制定容量 SLO，需固定视频规模、写入速率、持续时长、实例资源与故障注入，重复运行并统计分位数。
