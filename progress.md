# Progress

## 2026-05-15

- 根据 `AGENT.md` 梳理项目目标：软件工程师求职准备网站，前端 React，后端 Spring Boot。
- 建立架构设计文档、产品设计文档、API 契约和牛客 MCP server 对接设计。
- 创建前端和后端初始工程骨架，并复用根目录已有 `newcoder-mcp-server-main`。
- 按根目录已有 `newcoder-mcp-server-main` 调整后端配置，后端面经搜索通过 `nowcoder_search` 的讨论搜索 JSON 模式获取。
- 已补齐本机开发环境：Node.js/npm 安装到 `D:\devTools\more-offer-env\node`，JDK 21 安装到 `D:\devTools\more-offer-env\jdk-21`，Maven 安装到 `D:\devTools\more-offer-env\apache-maven`。
- 已为当前会话创建 `node`、`npm`、`npx`、`java`、`javac`、`mvn` wrapper，并写入用户级 `PATH`、`JAVA_HOME`、`MAVEN_HOME`。
- 重新构建 `newcoder-mcp-server-main` 成功，产物为 `newcoder-mcp-server-main/dist/index.js`。
- 为 `newcoder-mcp-server-main` 增加系统浏览器 fallback：优先读取 `NOWCODER_BROWSER_EXECUTABLE_PATH`，否则自动使用本机 Chrome/Edge，避免 `npx playwright install chromium` 下载卡住。
- 后端执行 `mvn -DskipTests package` 成功，产物为 `backend/target/more-offer-backend-0.1.0.jar`。
- 已启动本地联调服务：`newcoder-mcp-server-main` HTTP 健康检查 `http://127.0.0.1:3000/health` 返回 `ok`，后端健康检查 `http://127.0.0.1:8080/actuator/health` 返回 `UP`。
- 已通过后端业务接口 `POST /api/interview-experiences/search` 触发牛客 MCP 搜索，返回 8 条面经结果。
- 增加 `scripts/search-interviews.mjs` 用于在终端以 UTF-8 正常展示后端搜索到的牛客面经结果，避开 Windows PowerShell 5 对无 BOM 脚本和中文输出的乱码问题。

## 2026-05-16

- 启动 M1「Offer Prep Cockpit」：虚构产品经理 Lin Zhixia，并补充 `docs/pm-requirements.md`。
- 新增后端 `POST /api/preparation-plans/generate` 计划生成 API，输出准备分、重点领域、日任务、检查清单和风险提示。
- 重写前端工作台：增加面经、题单、计划三视图，支持从当前搜索上下文生成冲刺计划。
- 增加 CentOS 发布脚本 `scripts/deploy-centos.ps1`、systemd 服务和 nginx 配置示例，作为 M1 部署路径。
- 本地验证通过：`mvn test` 成功，`npm run build` 成功，页面烟测可点击生成计划且无控制台错误。
- M1 已发布到服务器 `/opt/more-offer/releases/m1-20260516013753`，`more-offer-backend.service` 为 active，服务器本机 `http://127.0.0.1/` 返回 More Offer 前端，`http://127.0.0.1:8080/actuator/health` 返回 `UP`。
- 服务器内测 `POST /api/preparation-plans/generate` 成功；`POST /api/interview-experiences/search` 可返回结构化空结果，MCP 调用链未崩溃。
- 公网验收发现 `www.lovenuaa.xyz:80` 未转发到当前服务器 nginx，外部仅 22 和 22366 端口可达；需要调整云防火墙或上层 NAT/端口映射后才能从公网打开网页。

## 2026-05-16 M2

- Started M2 "Interview Brief": added backend DTOs, controller, service, and tests for `POST /api/interview-briefs/generate`.
- Added frontend Brief tab and Build Brief action so candidates can turn current interviews/problems into priority signals, question clusters, STAR story prompts, and follow-up questions.

## 2026-05-16 M2 Deploy

- Local validation passed: `mvn test`, `npm run build`, backend package build, and a Playwright smoke test for the Build Brief flow.
- M2 deployed to `/opt/more-offer/releases/m2-20260516032757`; `/opt/more-offer/current` now points to this release.
- Server validation passed through nginx Host-header routing: frontend returns More Offer and `POST /api/interview-briefs/generate` returns the ByteDance interview brief with 4 clusters.
- Public validation still blocked: `www.lovenuaa.xyz:80` and `:443` time out from the local network after M2 deploy, while server-local nginx routing works. The remaining fix is upstream router/NAT forwarding to `192.168.31.227`.

## 2026-05-16 Port 9001 Update

- Confirmed deployment strategy change: ISP blocks `80` and `443`, so More Offer should avoid those ports.
- Updated nginx deployment config to listen on `9001`; backend stays on local `8080`.
- Applied the `9001` nginx vhost on the server, opened `9001/tcp` in firewalld, and reloaded nginx successfully.
- Public validation now passes: `http://www.lovenuaa.xyz:9001/` returns HTTP 200 and `POST /api/interview-briefs/generate` works through the same port.

## 2026-05-16 Search MCP Fix

- Root cause for Search appearing inactive: Nowcoder MCP was deployed, but Playwright had no browser binary on the headless CentOS server, so the tool returned an internal browser error that the backend previously converted into an empty result.
- Installed EPEL `chromium-headless` on the server and configured `NOWCODER_BROWSER_EXECUTABLE_PATH=/usr/lib64/chromium-browser/headless_shell`.
- Updated MCP browser launch for Linux headless operation with `--no-sandbox`, raised MCP/nginx timeouts for slow Nowcoder page loads, and changed default search terms to Chinese Nowcoder-friendly values.
- Deployed `/opt/more-offer/releases/m3-search-mcp-20260516094101`.
- Public validation passed: clicking Search on `http://www.lovenuaa.xyz:9001/` returned 20 result cards; first result title was `27实习-字节后端ai开发一面 1h`.

## 2026-05-16 Search Query Rewrite

- Fixed a second empty-result case where stale browser clients still sent English-heavy values such as `Java Backend Engineer`, `ByteDance`, and split keywords `first`, `round`.
- Backend now rewrites search input into Nowcoder-friendly terms, for example `字节跳动 Java 后端开发 一面 Spring Redis 面经`, and skips low-value English long phrases.
- Frontend keyword parsing now splits only on comma characters, so `first round` is preserved as one phrase when users type it.
- Deployed `/opt/more-offer/releases/m4-search-query-20260516094629`.
- Public validation with the stale English payload returned `total=20`; browser click validation also returned 20 cards.

## 2026-05-16 Git Push Proxy Note

- Recorded GitHub push troubleshooting: this Windows machine may fail to push if stale global Git proxy settings are present.
- Known fix: run `git config --global --unset http.proxy` and `git config --global --unset https.proxy`, then retry `git push origin main`.
- During the Web-Rooter milestone, the reliable push command was `git -c http.proxy= -c https.proxy= -c http.version=HTTP/1.1 push origin main`; this explicitly overrides any inherited proxy value for the one command.

## 2026-05-16 Web-Rooter Tech Radar

- Added backend configuration for `web-rooter-main` as a stdio MCP server and OpenAI Responses API-compatible summarization.
- Added `POST /api/tech-radar/research`, returning query, summary, themes, Web-Rooter article sources, interview signals, source status, and generation time.
- Switched Tech Radar from slow all-source `web_search_tech` to Web-Rooter MCP `web_fetch` against Hacker News Algolia and GitHub Search APIs for stable response time.
- Added backend normalization for Web-Rooter's extracted GitHub API text, because Web-Rooter converts large JSON payloads into text snippets instead of preserving the full JSON object.
- Added local fallback summarization for missing `OPENAI_API_KEY` or failed API calls.
- Added frontend Radar tab, Research Tech action, Web-Rooter article cards, summary panel badge, and metric count.
- Updated deployment assets to package the Web-Rooter runtime files, create `/opt/more-offer/runtime/web-rooter-venv`, set `WEB_ROOTER_PYTHON`, and load optional runtime secrets from `/opt/more-offer/.env`.
- Confirmed server Python 3.9 cannot install `mcp>=1.0`; installed application-private Miniconda Python 3.10 under `/opt/more-offer/runtime/miniconda3` for Web-Rooter.
- Fixed Web-Rooter dependency install strategy by preinstalling `greenlet==3.2.4` from a binary wheel; CentOS 7 cannot compile the latest source package with its default GCC.
- Added Web-Rooter browser runtime environment support so Python Playwright uses the existing glibc-217 Node runtime and `/usr/lib64/chromium-browser/headless_shell`.
- Patched Web-Rooter startup bootstrap to skip auto-installing bundled Chromium when the real headless Chromium path is already configured.
- Local validation passed for frontend build and backend tests before deployment work.
- Deployed `/opt/more-offer/releases/m5-web-rooter-final-20260516150957` to the server and kept public access on `http://www.lovenuaa.xyz:9001/`.
- Public validation passed: `/actuator/health` returned `UP`, `POST /api/tech-radar/research` returned 4 Web-Rooter-backed sources in about 5.7s, and `POST /api/interview-experiences/search` still returned 20 Nowcoder results.
- Browser validation passed on the public UI: clicking `Research Tech` rendered the Radar tab with 4 Web-Rooter sources and no app console errors.

## 2026-05-16 Chinese Tech Radar Sources

- Updated Tech Radar to prioritize Chinese developer sources for Chinese users: CSDN articles, Bilibili technology videos, V2EX discussions, and Web-Rooter social search are queried before Hacker News/GitHub.
- Added Bilibili API handling inside the bundled Web-Rooter MCP server so the backend can fetch Bilibili search results through MCP with browser-like public headers.
- Extended Bilibili handling to fetch a bounded hot-comment sample for the top videos through Web-Rooter MCP, so summaries can include comment-section signals instead of only video metadata.
- Increased frontend Radar requests from 8 to 12 sources and surfaced `publishedAt` in article cards.
- Local validation passed: backend `mvn test` and frontend `npm run build`.
- Prepared release package `m6-chinese-tech-radar-hot-comments-20260516200205`, but deployment is currently blocked because SSH to `www.lovenuaa.xyz` closes before the SSH banner (`Connection closed by remote host` / `ECONNRESET`) while port `9001` remains reachable.
- Current public server health is degraded while SSH is unavailable: `http://www.lovenuaa.xyz:9001/actuator/health` returns nginx `502`, and the root page returns `500`. The release package can be activated once SSH access recovers.
- Revalidated after the hot-comment change: backend `mvn test`, frontend `npm run build`, backend package build, `git diff --check`, and direct Bilibili reply API smoke test passed.
- Committed and pushed the hot-comment source update to `main` as `8f082e0 feat: include bilibili comment signals`.
- After server reboot restored SSH, logs showed heavy public SSH password spraying on May 16 and no `sshd` authentication entries during the later unreachable window. The evidence points to an SSH/upstream forwarding or daemon availability issue before authentication, not an application deployment command changing SSH config.
- Deployed `m6-chinese-tech-radar-hot-comments-20260516200205` after SSH recovered, then tightened source balancing so Bilibili cannot fill the entire Tech Radar result set when CSDN/V2EX are available.
- Public validation after source balancing found CSDN and V2EX results, then a cap-boundary bug skipped Bilibili after CSDN filled its quota; adjusted the Bilibili cap to add up to four items on top of existing Chinese sources.
