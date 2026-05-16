# Decision Log

## 2026-05-15

- 采用前后端分离 monorepo：`frontend`、`backend`、`newcoder-mcp-server-main`、`docs`。
- 牛客网面经获取必须通过根目录已有的 `newcoder-mcp-server-main` 专属 MCP server，前端和业务后端不直接抓取牛客页面。
- 后端用 `NiukeExperienceGateway` 抽象 MCP 调用，便于后续从 stdio 切换到远程服务。
- 初始阶段先设计结构化摘要和来源链接，不保存第三方站点全文。
- 仓库不保存任何服务器密码、Cookie 或第三方账号凭据；部署凭据通过本地环境或密钥管理提供。

## 2026-05-16

- 虚构产品经理为 Lin Zhixia，M1 目标从“资料检索”推进到“可执行备战计划”，详见 `docs/pm-requirements.md`。
- M1 暂不引入 MySQL、Redis 或登录系统；当前核心价值是单次检索后的计划生成，使用无状态 Spring Boot API 更轻、部署风险更低。
- 准备计划采用后端确定性规则生成，不调用付费大模型；这样能先验证交互闭环，并保证本地与服务器行为一致。
- 前端继续使用 React/Vite 与 lucide-react，不引入 Ant Design；当前界面是高密度工作台，定制 CSS 更容易保持信息扫描效率。
- 每个里程碑的部署优先用可重复脚本完成，服务器凭据不落库、不写入脚本。
- CentOS 7 服务器系统 Java 为 8，M1 使用应用私有 Temurin JRE 21 放在 `/opt/more-offer/runtime/jre-21`，不替换系统 Java。
- CentOS 7 glibc 为 2.17，MCP 运行时使用 unofficial Node.js 20 glibc-217 包放在 `/opt/more-offer/runtime/node`。
- 服务器 nginx 为宝塔路径 `/www/server/nginx`，vhost 放到 `/www/server/panel/vhost/nginx`；旧 `travel_diary_server` 的 80 端口配置已备份并让位给 More Offer。
- 当前公网 80 端口没有转发到这台服务器的 nginx，只有服务器内网和 SSH 可验证；需要在上层 NAT/安全组把 80/443 指向该主机。

## 2026-05-16 M2

- M2 scope is "Interview Brief": deterministic backend synthesis of interview signals into question clusters, story prompts, and follow-up questions. No MySQL, Redis, or paid model is introduced yet because the artifact can be derived from the current search context and should remain deployable on the existing CentOS 7 host.
- Xiaomi router login was tested once with the server credential and returned 401. I will not brute force router credentials; public 80/443 exposure remains blocked by upstream NAT until router or cloud network credentials are available.

## 2026-05-16 Network Port Decision

- The ISP blocks standard web ports `80` and `443` for this server path, so More Offer will not use those ports as public entry points.
- The nginx public listener is moved to `9001`; Spring Boot remains private on `127.0.0.1:8080`.
- Public access should use `http://www.lovenuaa.xyz:9001/` after the upstream router/NAT forwards external `9001` to this host.

## 2026-05-16 Nowcoder MCP Browser Decision

- The server is headless CentOS, which can run Playwright in headless mode.
- Playwright's default browser cache was missing and the default CDN/mirror download path was unreliable, so the server uses OS package `chromium-headless` from EPEL instead.
- The backend systemd service exports `NOWCODER_BROWSER_EXECUTABLE_PATH=/usr/lib64/chromium-browser/headless_shell`; MCP launch args include `--no-sandbox` for the current root-run service.
