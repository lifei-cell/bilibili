# 本地压测报告

测试日期：2026-08-07  
测试环境：Docker Desktop（总内存上限 7.648 GiB）、本项目完整 Compose 环境、JMeter 5.6.3（Docker 容器）与 k6。所有请求均通过网关 `:8080` 进入服务。

## 范围与判定标准

本轮混合读链路每个循环包含：

1. `GET /api/video/list?page=1&size=20&sort=hot`
2. `GET /api/video/{2001..2005}`
3. `GET /api/search?keyword=Java&page=1&size=20&sort=hot`

每个请求均校验响应 JSON 的 `success=true`。建议验收阈值为错误率小于 1%，视频列表/详情 P95 小于 300 ms，搜索 P95 小于 500 ms。上传、转码、播放接口、发弹幕等写入或计算密集场景未执行。

## 结果概览

| 场景 | 负载与时长 | 结果 |
| --- | --- | --- |
| JMeter 冒烟 | 5 用户，约 15 RPS，30 秒 | 444 请求，0 错误，P95 29 ms，P99 77 ms，最大 129 ms；通过。 |
| k6 短时读压 | 约 150 RPS，30 秒 | 4,503 请求，0 错误，整体 P95 11.61 ms；通过。 |
| k6 短时读压 | 约 300 RPS，30 秒 | 9,000 请求，0 错误，整体 P95 35.36 ms；短时通过。 |
| JMeter 持续读压 | 100 用户，目标 300 RPS，计划 5 分钟 | 未通过。JTL 有效请求窗口 230.03 秒，5,898 请求，实际平均 25.64 RPS，1,654 错误（28.04%）；JMeter 容器随后异常结束。 |
| k6 WebSocket | 100 连接，保持 10 秒 | 100% 建连成功；建连 P95 51.46 ms，鉴权消息 P95 63.05 ms；通过。 |

> k6 的 300 RPS 仅证明短时预热条件下可处理该吞吐；JMeter 的持续测试证明当前本机整套环境不能稳定维持 300 RPS。因此不能将“300 RPS”写成系统的持续承载能力。

## JMeter 持续读压明细

目标为总计 18,000 请求/分钟（约 300 RPS），使用 `loadtest/jmeter/bilibili-read.jmx` 执行。该计划的 Constant Throughput Timer 已配置为整个线程组共享的总吞吐。

| 接口 | 样本数 | 错误数 | 错误率 | 平均延迟 | P95 | P99 | 最大延迟 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 视频列表 | 2,001 | 577 | 28.84% | 4,020.72 ms | 7,045 ms | 65,180 ms | 171,790 ms |
| 视频详情 | 1,968 | 571 | 29.01% | 4,293.86 ms | 7,042 ms | 65,560 ms | 172,021 ms |
| 搜索 | 1,929 | 506 | 26.23% | 3,563.18 ms | 5,389 ms | 65,187 ms | 106,718 ms |

分段结果显示性能在第一个 30 秒窗口就开始退化：

| 时间窗口 | 实际 RPS | 错误率 | P95 |
| --- | ---: | ---: | ---: |
| 0–30 秒 | 126.97 | 0.00% | 2,816 ms |
| 30–60 秒 | 28.47 | 53.04% | 5,161 ms |
| 60–90 秒 | 20.00 | 100.00% | 5,334 ms |
| 90–120 秒 | 16.70 | 100.00% | 46,356 ms |
| 120–150 秒 | 1.17 | 100.00% | 43,538 ms |
| 150–180 秒 | 2.10 | 98.41% | 171,617 ms |

失败主要是 `HttpHostConnectException`（1,423 次）、`SocketTimeoutException`（200 次）和 `ConnectTimeoutException`（31 次），而非业务断言失败。这表明故障发生在网关可用性或基础设施资源层，而不是接口返回了 `success=false`。

## 运行环境事件与归因

- 压测末期 RocketMQ Broker 日志出现 `Killed`，容器随后重启；检查时 `restartCount=1`。重启后的 Broker 内存约 2.014 GiB，而 Docker 总内存上限仅 7.648 GiB。
- 网关在测试后恢复为 `UP`，其余主要服务仍运行；因此这不是一个可持续稳定的 300 RPS 结果。
- 视频详情接口本身不会发送播放计数消息：它只读取实时播放量；`sendViewMessage` 由播放接口调用。因此本轮 Broker 故障不能简单归因为详情接口直接写入 `VIEW_TOPIC`。
- 未采集测试峰值时的 JVM GC、线程池、MySQL 锁等待、Elasticsearch 延迟和 Docker 内存曲线，故不能把根因精确锁定到单个服务。现有证据支持“本机 Docker 资源预算与基础组件内存配置是首要嫌疑”，不支持“某一个 API 代码已被精确定位为唯一根因”。

## 结论

当前项目具备良好的短时读能力和 WebSocket 连接能力，但**不具备经验证的 300 RPS 持续读承载能力**。对于简历或答辩，应如实表述为：

> 在本地 Docker 微服务环境中完成 k6/JMeter 分层压测：混合读链路短时达到 300 RPS、0 错误，支持 100 路 WebSocket 弹幕长连接；持续 300 RPS 压测暴露了 RocketMQ 容器重启与资源预算不足的问题，并据此制定容量治理方案。

不要表述为“系统稳定承载 300 RPS”。当前有可信证据支持的稳定结果是 JMeter 约 15 RPS、30 秒，以及 k6 150/300 RPS、30 秒的短时结果；需要在资源隔离和观测完备后重测，才能给出持续容量结论。

## 优先优化项与复测方案

1. 为 RocketMQ、Nacos、Elasticsearch 和各 Java 服务显式设置容器内存限额与 JVM `-Xms/-Xmx`，并将 Docker Desktop 内存提高到能覆盖完整服务栈和压测机开销的预算。
2. 接入 Prometheus/Micrometer 或至少在压测窗口采集容器 CPU/内存、JVM GC、线程池、MySQL 连接/慢查询、RocketMQ 堆积和 Elasticsearch 延迟。
3. 将压测机与被测服务拆分到不同主机；当前发压端、网关和全部基础组件共享同一 Docker 内存池，会放大宿主资源争用。
4. 先以 50、100、150 RPS 各持续 5 分钟复测，全部满足错误率与 P95 阈值后，再逐档提高到 300 RPS；单次只改变一个变量。
5. 为播放、弹幕、上传和转码准备独立压测数据集，单独评估 RocketMQ 消费积压和 FFmpeg 资源竞争。

## 原始证据

- JMeter 冒烟 JTL：`loadtest/results/jmeter-smoke-20260807-134400/results.jtl`
- JMeter 冒烟 Dashboard：`loadtest/reports/jmeter-smoke-20260807-134400/index.html`
- JMeter 持续读压 JTL：`loadtest/results/jmeter-read-300rps-20260807-134503/results.jtl`
- JMeter 持续读压 Dashboard：`loadtest/reports/jmeter-read-300rps-20260807-134503/index.html`
- 可复跑测试计划：`loadtest/jmeter/bilibili-read.jmx`
