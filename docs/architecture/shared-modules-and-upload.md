# 公共模块与上传职责边界

## 模块依赖

| 模块 | 责任 | 使用方 |
| --- | --- | --- |
| `bilibili-common` | 无运行时依赖的响应、异常、跨服务消息契约；保留共享 Flyway SQL 资源 | 各服务 |
| `bilibili-common-observability` | Jackson 2 配置、指标/追踪依赖与 `observability.yml` | 网关和 Servlet 服务 |
| `bilibili-common-web` | Servlet 请求观测、校验；可选的 MyBatis 慢查询拦截器 | Servlet 服务 |
| `bilibili-common-reliability` | JDBC Outbox/Inbox、生命周期、管理接口和审计，不依赖 MQ SDK | 数据库服务 |
| `bilibili-common-messaging` | RocketMQ Outbox 发布适配，依赖 reliability | video、danmu、social、canal |
| `bilibili-common-storage` | JDK 类型的 `ObjectStorage` 接口及 MinIO 适配 | video |

Java 包名保持兼容，避免无意义的业务代码 import 迁移。基础模块不再传递 MVC、Redis、MyBatis、RocketMQ 或 MinIO；Maven Enforcer 阻止重新引入这些依赖。网关只选择基础类型和非 Servlet 的观测模块，不再用排除列表抵消 common 的依赖。

Redis、连接池、认证和 MyBatis starter 由实际使用服务显式声明。web 的 MyBatis 依赖为 `optional`，仅用于编译受 `@ConditionalOnClass` 保护的慢查询拦截器，不会向搜索服务等调用方传递 MyBatis。用户服务保留已有消息管理查询接口，因此依赖 reliability，但不引入 MQ 发布适配或 MinIO。

共享数据库迁移仍位于 `bilibili-common/src/main/resources/db/migration`，不改既有 SQL、校验和或版本；SQL 作为共享 schema 契约不会引入 JDBC 驱动。所有模块仍由根 POM 管理版本，容器继续打包各服务可执行 JAR。

## 上传职责

- `UploadServiceImpl`：两套 HTTP API 的兼容入口、秒传查询、流程编排。
- `UploadSessionManager`：Redis 会话缓存、过期刷新、断点任务恢复、限流和带令牌的合并锁。
- `UploadChunkValidator`：任务所有者、文件身份、分片数量、末片大小与流式 MD5 校验。
- `UploadChunkRepository`：分片元数据读写、重复分片、按序完整性检查和完成标记。
- `UploadMergeService`：独立 Spring Bean，事务内完成合并流程与转码任务交接；两套入口均调用该代理。
- `UploadTranscodeTasks`：转码任务创建/复用及授权状态查询。
- `UploadStorage`：上传业务对象名、URL 和异常翻译，依赖 `ObjectStorage`，不接触 MinIO SDK。
- `MinioObjectStorage`：对象上传、存在性检查、按序组合；仅明确的不存在错误返回 false，其他存储故障继续抛出。

合并流程保持原有业务行为：分片校验 → 对象组合（已有目标则复用）→ 转码任务落库 → 分片完成标记 → 会话续期。数据库失败触发回滚，锁按原令牌释放；对象存储不参与数据库事务，失败后已有目标对象仍依靠重试复用。原有五分钟合并锁、对象命名和直传逻辑未改为新的并发协议，本次不宣称跨存储原子提交。

## 验证入口

从根目录运行 Maven，`-am` 会自动包含新增模块：

```bash
mvn -s .mvn/settings.xml --batch-mode --no-transfer-progress \
  -Dit.test=UserApiIT,MessageArchiveMySqlIT,MessageReplayMySqlIT,MessageInboxLeaseMySqlIT,OutboxLeaseMySqlIT \
  -Dfailsafe.failIfNoSpecifiedTests=false clean verify
```

该命令运行各模块单元测试、指定 MySQL/API 集成测试及覆盖率门禁，不等同于完整媒体链路验收。新增上传用例覆盖末片校验、MD5、越权、对象复用、存储错误与合并事务提交/回滚；既有上传入口用例继续覆盖秒传、重复分片、断点进度和转码状态。
