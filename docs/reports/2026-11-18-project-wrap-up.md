# 项目收尾验证记录

日期：2026-11-18。基线：`ad0204f`；本报告随 CI 与交付文档修改提交。

## 修改

- 删除重复的前端浏览器工作流，保留统一 CI 中相同的 Chromium 回归与诊断附件。
- 发布验证等待三个基础门禁通过，并在独立运行器中准备 MinIO 测试镜像，供其 Maven 集成测试使用。
- 新增项目交付入口，修正 README 的 HLS 产物说明和面试文档的工作流链接。

## 本次检查

| 检查 | 结果 |
| --- | --- |
| `npm --prefix bilibili-web run quality` | ESLint、14 项单元测试、类型检查、生产构建全部通过 |
| `CI=true npm --prefix bilibili-web run test:e2e` | 3 项 Chromium 模拟网关场景通过，11.9 秒，无重试 |
| 三份 Compose 文件合并 `config --quiet` | WSL Docker 校验通过 |
| CI YAML 解析与结构检查 | 发布依赖三个门禁、仅一个模拟浏览器执行入口、两个后端验证运行器均准备 MinIO 镜像 |
| 文档相对链接与 `git diff --check` | 通过 |

环境：Windows，Node.js v24.14.0；Compose 校验使用 WSL Ubuntu-26.04 / Docker Engine 29.9.1。
首次浏览器运行因本地缺少 Playwright Chromium v1243 未能启动；执行
`npx playwright install chromium` 安装后重跑通过，没有修改测试断言。

Vitest 覆盖率针对已配置的 API/服务文件，行覆盖率 100%，不代表整个前端覆盖率。
Vite 构建仍提示播放页分包约 607 kB；该提示不阻止构建，本次未调整打包策略。

## 未覆盖

本次没有改动 Java 或前端业务代码，没有重跑后端全套、真实 Compose 媒体链路、真实浏览器或容量测试。
后端历史结果见 [2026-11-04 报告](2026-11-04-transcode-fault-matrix.md)。
本地 YAML 检查不代表远端 GitHub Actions 已执行成功；远端分支保护设置未核查。
本次属于工程交付收尾，不宣称生产上线验收完成。
