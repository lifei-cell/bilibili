# ES 无空窗重建实施记录（2026-09-17）

## 实现

- 搜索服务和 Canal 消费端统一使用 `video_search` Alias；初次部署自动挂接已有 `video_index`，新环境创建初始物理索引。
- 重建使用 `v.id > cursor order by v.id limit 200` 分页写入新物理索引。旧 Alias 不变，搜索继续可用。
- 切换前暂停本进程的视频 CDC 索引写入，重新分页追平、删除孤儿、刷新并对账（文档数及字段），通过后原子交换 Alias。失败保留旧 Alias 与旧索引。
- 管理接口 `/api/admin/reliability/es/rollback?targetIndex=...` 支持对保留旧索引重新追平后切回；目标索引限白名单模式。回退索引名从重建响应 `previousIndex` 取得。

## 验证与边界

- Maven 编译和定向单元测试通过，覆盖分页、对账失败不切换、CDC 消费路径和 MySQL 命名锁释放/竞争。
- 真实双 Canal 容器演练已通过：两个实例均连接同一 Canal，重建并发时仅一个实例完成 Alias 切换；Alias 保持单索引，对账一致，更新后的标题可搜索。完整证据见 `docs/reports/2026-09-17-p1-reliability-drill.md`。
- 重建最终追平和视频 CDC 写入共用 MySQL 命名锁，配合各进程内读写锁完成跨实例协调。当前证据是本地 Docker 单次演练，不替代规模化压测；切换阶段 CDC 可能积压，需补锁等待、积压恢复和多节点容量 SLO。搜索 Redis 缓存最多 30 秒过期。
