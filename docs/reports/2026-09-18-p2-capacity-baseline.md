# P2 分场景容量基线报告

> 状态：**部分完成，转码场景未通过，不能把本次结果当作完整容量承诺。**

## 1. 执行记录

- 有效运行：`p2-capacity-baseline-20260918-093234`
- 测量提交：`74579d1bf64e5eb29d26b6c37431954571439e13`；工作区当时包含既有未提交改动。
- 主机：Windows 11，32 个逻辑处理器，约 39.8 GiB 内存。
- Docker：Docker Desktop 4.88.1，Engine 29.7.2，`desktop-linux`，Linux amd64。
- Compose：`docker-compose.yml`、`docker-compose.service.yml`、`docker-compose.e2e.yml`；转码阶段叠加 `docker-compose.p2.yml`，启动 2 个专用 Worker。
- 采样：预热 10 秒，测量 30 秒，资源/MQ 每 5 秒采样。
- 资源门禁：默认 CPU 配额使用率/内存 85%，RocketMQ Broker CPU 120%，MQ 积压 1000；延迟和错误阈值见 `loadtest/capacity-baseline.json`。

## 2. 有效测量结果

| 场景 | 实际负载 | 请求/迭代数 | P95/P99 | HTTP 错误率 | 业务错误率 | CPU 配额峰值 | 内存峰值 | MQ 峰值 | 场景门禁 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 读 | 50 iterations/s，约 150 HTTP RPS | 4,503 / 1,501 | 16.46 / 38.65 ms | 0 | 0 | 64.35% | 55.52% | 0 | 通过 |
| 写 | 1 operation/s，评论创建后删除 | 67 / 31 | 51.00 / 53.10 ms | 0 | 0 | 10.53% | 60.51% | 0 | 通过 |
| 弹幕 | 1 message/s，唯一 requestId，结束后校验落库 | 36 / 31 | 32.98 / 33.58 ms | 0 | 0 | 93.00%（Broker，适用 120% 例外） | 60.77% | 3 | 通过 |
| 转码 | 1 task/s，固定 MP4，2 Worker | 67 / 31 | 30.00 / 30.70 ms（仅直传请求） | 46.27% | 100% | 13.10% | 61.23% | 3 | **未通过** |

读、写、弹幕的延迟、错误、资源和 MQ 门禁均满足当前配置。弹幕 CPU 峰值来自 RocketMQ Broker，未超过该组件的 120% 配额例外。

## 3. 转码失败边界

31 次转码迭代均完成了登录和直传初始化，但 k6 容器访问 MinIO 预签名 URL 时，上传阶段全部失败，未进入 `complete`、Worker 转码和 HLS 完成等待。因此转码 P95/P99 不能解释为转码耗时，不能用于推导转码吞吐。

已提交修复 `23f4a51`：访问 Docker 宿主机端口时保留预签名 URL 的原始 `Host`，并在上传失败时记录 HTTP 响应。按用户要求停止后续运行，该修复只做了 Node 语法检查，尚未重新取得真实转码通过证据。

## 4. 原始报告

原始结果目录被 `.gitignore` 忽略，未提交仓库；目录内保留环境、Compose 展开配置、Docker 版本/信息、E2E SLO、k6 JSON、stdout/stderr 和资源/MQ 采样：

`loadtest/results/p2-capacity-baseline-20260918-093234/`

- 汇总：`p2-capacity-baseline.json`、`p2-capacity-baseline.md`
- 环境：`environment.json`、`docker-version.json`、`docker-info.json`、`compose-config.yml`
- E2E 固件：`fixture-e2e-slo.json`、`fixture-e2e.out.log`；清理：`fixture-cleanup.out.log`
- 读/写/弹幕/转码：各自 `warmup-k6.json`、`measurement-k6.json` 及对应 `.out.log`、`.err.log`

另一次默认读压力试跑（`loadtest/results/p2-capacity-baseline-20260918-091848/`）在约 300 HTTP RPS、0 错误下使 Gateway CPU 配额峰值达到 98.97%，超过 85% 门槛；该结果只作为饱和边界，不作为通过基线。用户要求停止后的运行 `p2-capacity-baseline-20260918-094333` 在 E2E 阶段被中止，不纳入容量结论。

## 5. 证据边界

本报告是当前 Windows Docker Desktop、固定演示数据、固定容器配额和短时负载下的本地复验。它不代表生产容量、远端 CI 或其他编码器/硬件矩阵，也不把读链路吞吐外推到写入、转码或弹幕。完整 P2 通过条件仍是重新验证预签名直传后，转码任务真正完成并通过 P95/P99、错误率、Worker 资源和 MQ 门禁。
