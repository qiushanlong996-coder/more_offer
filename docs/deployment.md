# Deployment

M1 deploys as a simple CentOS release:

- Spring Boot backend runs as `more-offer-backend.service`.
- Vite static files are served by nginx from `/opt/more-offer/current/frontend/dist`.
- nginx listens on external port `9001` and proxies `/api/` and `/actuator/` to `127.0.0.1:8080`.
- The backend invokes the dedicated Nowcoder MCP server through the packaged `newcoder-mcp-server-main/dist/index.js`.
- The service expects app-owned runtimes at `/opt/more-offer/runtime/jre-21` and `/opt/more-offer/runtime/node`.

## Command

Run from the repository root:

```powershell
.\scripts\deploy-centos.ps1
```

The script uses SSH/SCP and expects credentials to be supplied by the local SSH agent, terminal prompt, or another local secret mechanism. Do not commit passwords, cookies, or private keys.

## Server Prerequisites

- JDK 21 available at `/usr/bin/java`.
- Or app-owned JRE 21 available at `/opt/more-offer/runtime/jre-21/bin/java` (the provided service uses this path).
- Node.js 18+ available at `/opt/more-offer/runtime/node/bin/node` for the Nowcoder MCP server.
- Headless Chromium available at `/usr/lib64/chromium-browser/headless_shell` for the Nowcoder MCP server. On CentOS 7, install `chromium-headless` from EPEL and run Playwright with `--no-sandbox` because the backend service currently runs as root.
- nginx installed if the public web frontend should be served from the same host.
- systemd enabled.
- Ports `80` and `443` are blocked by the ISP for this server path. Do not use them as public entry points for this project.
- Port `9001` should be opened on the server firewall and forwarded by the upstream router/NAT to this host. The public URL should use `http://www.lovenuaa.xyz:9001/` unless a later tunnel/CDN solution is added.
- Port `8080` must remain available locally for the backend; it does not need to be exposed publicly.

## Network Constraint

The current deployment path is behind an ISP policy that blocks standard web ports `80` and `443`. More Offer should therefore avoid binding the public nginx virtual host to those ports. The chosen public application port is `9001`; nginx serves the frontend on `9001` and keeps backend traffic private on `127.0.0.1:8080`.

## Headless Browser

The server is a non-GUI CentOS host. This is compatible with Playwright as long as the MCP server launches a headless browser. The deployed setup uses EPEL `chromium-headless` at `/usr/lib64/chromium-browser/headless_shell`, and `deploy/more-offer-backend.service` exports `NOWCODER_BROWSER_EXECUTABLE_PATH` so the MCP process does not depend on Playwright's browser download cache.

## GitHub Push Troubleshooting

If `git push origin main` fails with GitHub connection reset or port `443` timeout on this Windows development machine, check whether Git is still using a stale global proxy. The known fix is:

```powershell
git config --global --unset http.proxy
git config --global --unset https.proxy
```

After unsetting the proxy, retry:

```powershell
git push origin main
```
