# ES 无空窗重建实施记录（2026-09-17）

## 实现

- 搜索服务和 Canal 消费端统一使用 `video_search` Alias；初次部署自动挂接已有 `video_index`，新环境创建初始物理索引。
- 重建使用 `v.id > cursor order by v.id limit 200` 分页写入新物理索引。旧 Alias 不变，搜索继续可用。
- 切换前暂停本进程的视频 CDC 索引写入，重新分页追平、删除孤儿、刷新并对账（文档数及字段），通过后原子交换 Alias。失败保留旧 Alias 与旧索引。
- 管理接口 `/api/admin/reliability/es/rollback?targetIndex=...` 支持对保留旧索引重新追平后切回；目标索引限白名单模式。回退索引名从重建响应 `previousIndex` 取得。

## 验证与边界

- Maven 编译和定向单元测试通过，覆盖分页、对账失败不切换和 CDC 消费路径。
- Docker Engine 可连接，但当前没有运行中的项目容器；本次未执行真实 MySQL/ES/RocketMQ 端到端演练，不把单元测试等同于发布验收。
- 当前写入门禁是 Canal 进程内锁，部署多个 Canal 实例时需先加入分布式切换协调；切换阶段 CDC 可能积压，但搜索继续使用旧索引。搜索 Redis 缓存最多 30 秒过期。
