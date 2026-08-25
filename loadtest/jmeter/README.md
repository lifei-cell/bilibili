# JMeter 压测计划

`bilibili-read.jmx` 是无需第三方插件的混合读压测计划，按一次视频列表、一次视频详情和一次搜索组成一个循环。每个请求都校验响应 JSON 的 `success` 字段为 `true`；HTTP 非 2xx/3xx 响应也会由 JMeter 标记为失败。

## 前置条件

1. 启动项目完整 Compose 环境，并确认网关可通过 `http://127.0.0.1:8080` 访问。
2. 安装 Apache JMeter，并使用 JDK 启动。
3. 保证演示视频 `2001` 到 `2005` 已存在且已发布；计划会随机请求其中一个详情页。

在 GUI 中先打开 `.jmx` 并以少量线程验证请求即可；正式压测不要启用 `View Results Tree`，使用命令行执行。

## 快速验证

以下示例以约 15 RPS 运行 30 秒：

```powershell
& 'C:\tools\apache-jmeter\bin\jmeter.bat' -n `
  -t 'D:\projects-2\bilibili\loadtest\jmeter\bilibili-read.jmx' `
  -Jthreads=5 -Jramp_up=5 -Jduration=30 -Jtarget_rpm=900 `
  -l 'D:\projects-2\bilibili\loadtest\results\smoke.jtl'
```

## 300 RPS、5 分钟基线

`target_rpm` 表示所有 HTTP 请求的总速率。该计划一个循环有 3 个请求，因此 `18000 RPM` 即约 `300 RPS`。报告目录必须不存在或为空。

```powershell
$runId = Get-Date -Format 'yyyyMMdd-HHmmss'
$resultDir = "D:\projects-2\bilibili\loadtest\results\$runId"
$reportDir = "D:\projects-2\bilibili\loadtest\reports\$runId"
New-Item -ItemType Directory -Force -Path $resultDir | Out-Null

& 'C:\tools\apache-jmeter\bin\jmeter.bat' -n `
  -t 'D:\projects-2\bilibili\loadtest\jmeter\bilibili-read.jmx' `
  -Jthreads=100 -Jramp_up=30 -Jduration=300 -Jtarget_rpm=18000 `
  -l "$resultDir\read.jtl" -e -o $reportDir
```

完成后打开 `$reportDir\index.html`，重点查看每个接口的错误率、P95/P99 和吞吐。先依次执行 50、150、300 RPS，再逐档提高；任一接口错误率超过 1%、P95 超过目标或服务出现持续积压时停止加压。

## 可覆盖参数

| 参数 | 默认值 | 含义 |
| --- | --- | --- |
| `protocol` | `http` | 访问协议 |
| `host` | `127.0.0.1` | 网关主机 |
| `port` | `8080` | 网关端口 |
| `threads` | `100` | 并发 JMeter 用户数上限 |
| `ramp_up` | `30` | 线程升压时间，单位秒 |
| `duration` | `300` | 压测持续时间，单位秒 |
| `target_rpm` | `18000` | 总 HTTP 目标吞吐，单位请求/分钟 |
| `timeout_ms` | `5000` | 连接与响应超时，单位毫秒 |

若使用本机安装的 JMeter，保持默认 `-Jhost=127.0.0.1` 即可。若使用本项目实测的
`alpine/jmeter:5.6.3` 镜像，请使用无点号别名，避免其启动包装脚本错误拆分
`host.docker.internal`：`--add-host=hostgateway:host-gateway -Jhost=hostgateway`。

## WebSocket 弹幕

WebSocket 需要在 JMeter Plugins Manager 安装 `WebSocket Samplers by Peter Doornbosch`（插件 ID：`websocket-samplers`）后配置。对本项目使用：`WebSocket Open Connection` 连接 `ws://127.0.0.1:8080/api/danmu/ws/2001`，读取首条 `auth` 消息并断言 `success=true`，循环发送 `{"type":"heartbeat"}`，结束时使用 `WebSocket Close Connection`。

该插件不是项目依赖，未随仓库提交；当前仓库内的 `../ws-connections.js` 已可在 k6 中完成同等的 100 路连接基线。上传、转码、发弹幕等写场景会改变数据或占用 FFmpeg，应准备独立压测数据后再实施。
