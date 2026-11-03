# 消息生命周期实现与验证（2026-11-02）

## 实现范围

- V11 增量迁移：Outbox 归档表、Inbox 精简成功凭证、重放审计、重放计数与归档索引。
- Outbox 默认 7 天后归档；Inbox 默认 30 天后压缩成功明细，保留永久去重身份。没有对失败、处理中消息或永久凭证做自动删除。
- 归档事务、源记录行锁、分批及 `SKIP LOCKED`；领取前后当前读覆盖归档与重放竞态。
- Outbox 重试耗尽进入 `EXHAUSTED`，保留有效租约，查询元数据、归档和重放审计。
- Outbox 与 DLQ 统一受控重放：管理员令牌、必填请求编号/版本/原因、幂等请求、并发串行化、冷却及次数上限。
- DLQ 重放事务化进入 Outbox，不直接调用 MQ；保持业务载荷和 Inbox 身份，成功凭证命中也能收敛 DLQ 状态。
- 发布器发送原始 JSON 字节，不要求当前服务拥有其他服务的消息类；MQ 转换器线格式由单元测试验证。
- 新增耗尽、归档失败和归档停滞告警；Promtool 测试加入 CI。

## 验证记录

第一轮定向 `clean verify`（common、user、video）通过，包含用户 HTTP API 与 V11 迁移、既有 Inbox/Outbox 租约回归、新增 6 条归档 MySQL 用例和 7 条重放 MySQL 用例。

Prometheus 3.5.0 的 Promtool 规则测试通过：耗尽告警延迟、归档失败、归档停滞及关闭维护时不误报。

2026-11-03 最终复验：在根目录执行以下命令，9 个 Maven 模块全部 `SUCCESS`，各模块单元测试及已有覆盖率门禁通过。集成测试明确限定为用户 API、6 条归档用例与 7 条重放用例，包含最终加强的整批归档回滚测试；本命令不是全部集成测试。

```bash
mvn -s .mvn/settings.xml --batch-mode --no-transfer-progress \
  -Dit.test=MessageArchiveMySqlIT,MessageReplayMySqlIT,UserApiIT \
  -Dfailsafe.failIfNoSpecifiedTests=false verify
```

同日再次执行 Promtool 规则测试，结果为 `SUCCESS`。本地构建日志为 `lifecycle-resume-verify.log`（忽略文件，不纳入版本库）。

完整保留边界、配置、HTTP 接口变更和升级要求见 [运维手册](../runbooks/message-lifecycle.md)。此前全量发布验证中的 MinIO 镜像访问问题不属于本次生命周期验证范围；本报告不宣称完整媒体链路或全量发布已通过。
