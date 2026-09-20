# Linux curl 命令解析修复

## 触发证据

- GitHub Actions 手动发布验收：`CI #30`，run `35491697412`，提交 `f04254c`。
- Backend verify、Frontend quality、Compose validation 均通过。
- Release validation 在等待 Gateway 路由收敛时失败，日志显示 PowerShell 尝试执行 `'/usr/bin/curl /bin/curl'`。
- 失败产物：`release-validation-evidence-35491697412`，artifact `10598914666`。

## 根因

Linux runner 上 `Get-Command curl -CommandType Application` 同时解析到 `/usr/bin/curl` 和 `/bin/curl`。脚本直接读取集合的 `Source`，调用运算符因此收到拼接后的多路径字符串。Windows 本地仅返回一个 `curl.exe`，未暴露该问题。

## 修复

`scripts/e2e/compose-helpers.ps1` 对 Windows 与 Unix 两条命令发现路径都显式选择首个应用程序结果，保证 `$script:CurlExecutable` 始终为标量路径。

## 验收边界

- 使用 Linux PowerShell 容器运行 `scripts/verification/verify-compose-helpers-portability.ps1`，断言 curl 路径为单一可执行文件并完成真实 `curl --version` 调用。
- 本地后端全量 `mvn clean verify` 与合并 Compose 配置校验继续作为回归门禁。
- 修复提交推送后重新执行 GitHub Actions 手动发布验收；只有远端产物 `passed=true` 才关闭此项。
