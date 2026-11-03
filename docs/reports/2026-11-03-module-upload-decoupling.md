# 公共模块与上传解耦（2026-11-03）

## 变更范围

- common 收敛为无运行时依赖的基础类型和共享 SQL 资源，新增 observability、web、reliability、messaging、storage 五个按需模块。
- 可靠性数据库逻辑与 RocketMQ 发布器分离；MinIO 仅由视频服务通过存储适配模块引入。
- 各业务服务显式声明 Redis、认证、MyBatis；网关、搜索不再维护 common 的反向依赖排除列表。
- 添加基础模块及网关/搜索/用户/可靠性模块的 Maven 依赖边界门禁。
- 上传实现由原 687 行拆成 API 编排、会话、校验、分片元数据、合并事务、转码交接、存储七项职责；入口为 238 行，其他职责类最大 162 行。保留两套接口及原对象名、TTL、限流和重试行为。
- 修复文档中的移动源码链接；SQL 迁移版本及内容不变。

设计和边界见 [架构说明](../architecture/shared-modules-and-upload.md)。

## 验证范围

14 个模块编译打包通过。可执行 JAR 检查确认网关不含 MVC/MyBatis/MQ/MinIO，用户服务不含 MQ/MinIO，视频服务保留所需适配。

网关在关闭服务发现及对应动态路由发现、随机端口条件下独立启动成功；该检查验证类路径和 Spring 装配，不代表后端路由或外部服务可用。

搜索服务已完成 Spring 装配，但在 ApplicationReadyEvent 的索引别名初始化中因未连接 Elasticsearch 退出，因此不宣称搜索服务完整启动成功。该次检查没有遇到移除 MyBatis/MQ/MinIO 后的缺类错误。

新增上传测试验证末片大小、流式 MD5、任务越权、目标对象复用、存储错误传播，以及新旧入口经过 Spring 事务代理时的提交与回滚。存储适配测试区分对象缺失、授权失败和传输异常；存储使用 mock，未将其宣称为真实 MinIO 上传验收。

第一轮定向回归中，上传相关 15 条测试及以下 28 条 MySQL 用例全部通过（失败、错误、跳过均为 0）：归档 6 条、Inbox 租约 9 条、受控重放 7 条、Outbox 租约 6 条。该轮进程在最终 Reactor 汇总前中断，因此这些结果只作为已完成用例的证据，不宣称该轮整条 Maven 命令成功。

最终补跑命令如下，14 个模块全部 `SUCCESS`，各模块单元测试、用户 HTTP API 集成测试、依赖边界检查和既有覆盖率门禁全部通过。视频模块为 53 条单元测试，其中上传相关 15 条；最终命令未重复运行此前已通过的 28 条消息 MySQL 用例。

```bash
mvn -s .mvn/settings.xml --batch-mode --no-transfer-progress \
  -Dit.test=UserApiIT -Dfailsafe.failIfNoSpecifiedTests=false verify
```

本地忽略日志：`decoupling-build.log`、`decoupling-verify.log`、`decoupling-final-verify.log`。完整媒体链路和真实 MinIO 上传未在本次验证范围内。
