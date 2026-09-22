# Linux curl no-proxy 通配符修复

## 触发证据

- GitHub Actions 手动发布验收：`CI #32`，run `35492852212`，提交 `625ab24`。
- Backend verify、Frontend quality、Compose validation 均通过。
- Release validation 已越过 curl 可执行文件解析，但调用 `POST /api/user/login` 时返回无效 JSON。
- 日志显示 curl 将仓库目录中的文件与目录逐项当成主机名，请求前连续报告 `Could not resolve host`。
- 失败产物：`release-validation-evidence-35492852212`，artifact `10599782106`，SHA-256 `967652d0b327f3eed19b7c13dad1cbac29e06759796004512c8f8414ee5e6e5b`。

## 根因

Linux PowerShell 调用原生命令时会对独立参数 `*` 做路径通配符展开。原脚本用两个参数表达 `--noproxy '*'`，因此 curl 接收了仓库条目而不是通配符配置。Windows 本地没有暴露相同行为。

首次改用 `NO_PROXY/no_proxy='*'` 后，封装函数仍通过隐式 `$args` 转发 curl 参数；Linux 探针中参数数组没有可靠绑定，curl 收到空参数并报告 `no URL specified!`。此外，探针用 `[Uri]$probePath` 构造 `file://` 地址只在 Windows 得到绝对 URI，Linux 下返回空 `AbsoluteUri`。

## 修复

- 在 `compose-helpers.ps1` 增加 `Invoke-CurlNoProxy`：调用方通过显式 `string[] CurlArguments` 传入完整 curl 参数；封装仅在子进程调用期间设置 `NO_PROXY/no_proxy='*'`，随后恢复原环境，避免污染 Maven、Docker 等其他工具。
- E2E、P1 演练、写路径 SLO 与容量采样统一通过显式参数数组调用；隔离的 PowerShell Job 在自身生命周期内设置相同环境变量。
- portability 回归使用 `UriKind.Absolute` 构造跨平台 `file://` 地址，并在仓库工作目录下执行真实请求，验证参数不丢失且通配符不会扩展为仓库条目。
- 远端完整验收进一步暴露 Linux 原生参数边界与冷启动差异：HTTP header 值统一为无空格的 `Name:Value` 参数，E2E 将 `AdminToken` 显式注入 Compose 环境；MinIO 客户端改用 `quay.io/minio/mc` 的固定 manifest digest，避免本机缓存掩盖 Docker Hub `minio/mc:latest` 已不可拉取。

## 验收边界

- Windows 与 Linux PowerShell 容器均须通过 `verify-compose-helpers-portability.ps1`。
- 后端全量 `mvn clean verify` 与 Compose 合并配置继续通过。
- 修复提交推送后重新执行 GitHub Actions 手动发布验收；远端报告 `passed=true` 才关闭此项。
