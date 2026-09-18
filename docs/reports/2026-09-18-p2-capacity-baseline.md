# P2 分场景容量基线报告

> 状态：**本地 Docker 基线门禁通过；结果只适用于本报告绑定的环境、数据集和负载。**

## 1. 执行记录

- 有效运行：`p2-capacity-baseline-20260918-132647`
- 测量提交：`9fcd049d5b9062e11454d3b26edf9b913335d2e6`；工作区仍包含既有未提交改动。
- 主机：Windows 11，32 个逻辑处理器，约 39.8 GiB 内存。
- Docker：Docker Desktop 4.88.1，Engine 29.7.2，`desktop-linux`，Linux amd64。
- Compose：`docker-compose.yml`、`docker-compose.service.yml`、`docker-compose.e2e.yml`；转码阶段叠加 `docker-compose.p2.yml`，启动 2 个专用 Worker。
- 采样：预热 10 秒，测量 30 秒，资源/MQ 每 5 秒采样。
- 资源门禁：默认 CPU 配额使用率/内存 85%，RocketMQ Broker CPU 120%，MQ 积压 1000；延迟和错误阈值见 `loadtest/capacity-baseline.json`。

## 2. 有效测量结果

| 场景 | 实际负载 | 请求/迭代数 | P95/P99 | HTTP 错误率 | 业务错误率 | CPU 配额峰值 | 内存峰值 | MQ 峰值 | 场景门禁 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 读 | 50 iterations/s，约 150 HTTP RPS | 4,500 / 1,500 | 18.62 / 47.10 ms | 0 | 0 | 104.58%（Broker，适用 120% 例外） | 54.73% | 0 | 通过 |
| 写 | 1 operation/s，评论创建后删除 | 67 / 31 | 98.00 / 99.70 ms | 0 | 0 | 9.45% | 55.23% | 0 | 通过 |
| 弹幕 | 1 message/s，唯一 requestId，结束后校验落库 | 36 / 31 | 173.24 / 320.03 ms | 0 | 0 | 28.33% | 55.93% | 4 | 通过 |
| 转码 | 1 task/s，固定 MP4，2 Worker | 167 / 30 | 2,129.10 / 2,137.10 ms | 0 | 0 | 34.93%（Worker） | 56.04% | 4 | 通过 |

四个场景的延迟、错误、资源和 MQ 门禁均满足当前配置。读场景的 CPU 峰值来自 RocketMQ Broker，未超过该组件的 120% 配额例外；转码任务 30 次完成，k6 原始输出记录 1 次 dropped iteration，需结合原始报告解读目标到达率。

## 3. 转码验证

本轮 30 次转码任务均完成登录、预签名直传、`complete`、双 Worker 消费和 HLS 状态轮询；HTTP/业务错误率均为 0，转码 E2E P95/P99 为 2.129/2.137 秒。`transcode_upload_latency` P95/P99 为 91.55/93.42 ms，`transcode_completion_latency` 平均 1.42 秒。

修复 `23f4a51` 已通过真实 Docker/k6 验证：访问 Docker 宿主机端口时保留预签名 URL 的原始 `Host`，并在上传失败时记录 HTTP 响应。

## 4. 原始报告

原始结果目录被 `.gitignore` 忽略，未提交仓库；目录内保留环境、Compose 展开配置、Docker 版本/信息、E2E SLO、k6 JSON、stdout/stderr 和资源/MQ 采样：

`loadtest/results/p2-capacity-baseline-20260918-132647/`

- 汇总：`p2-capacity-baseline.json`、`p2-capacity-baseline.md`
- 环境：`environment.json`、`docker-version.json`、`docker-info.json`、`compose-config.yml`
- E2E 固件：`fixture-e2e-slo.json`、`fixture-e2e.out.log`；清理：`fixture-cleanup.out.log`
- 读/写/弹幕/转码：各自 `warmup-k6.json`、`measurement-k6.json` 及对应 `.out.log`、`.err.log`

此前约 300 HTTP RPS 的读压力试跑（`loadtest/results/p2-capacity-baseline-20260918-091848/`）在 0 错误下使 Gateway CPU 配额峰值达到 98.97%，超过 85% 门槛；该结果只作为饱和边界，不作为通过基线。旧的失败转码运行 `p2-capacity-baseline-20260918-093234/` 和被中止的 `p2-capacity-baseline-20260918-094333/` 均保留为调试轨迹，不替代本轮通过报告。

## 5. 证据边界

本报告是当前 Windows Docker Desktop、固定演示数据、固定容器配额和短时负载下的本地复验。它不代表生产容量、远端 CI 或其他编码器/硬件矩阵，也不把任一场景结果外推到生产。后续容量工作应在目标部署环境重复同一负载，并补充编码器和硬件矩阵。
