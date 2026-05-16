# Agent Notes

This project is a web app for software engineer job search preparation. It targets desktop and mobile browsers. Core features include collecting Nowcoder interview experiences and high-frequency coding problems, then turning them into interview preparation plans.

## Tech Stack

- Frontend: React / Vite / TypeScript
- Backend: Java / Spring Boot
- Nowcoder integration: dedicated MCP server in `newcoder-mcp-server-main`

## Working Rules

1. Record progress in `progress.md` after each development milestone.
2. Record product and technical decisions in `decision.md`.
3. Test each feature with browser automation or an equivalent end-to-end smoke test.
4. After tests and deployment validation pass, commit code to git. The default branch is `main`.
5. Keep secrets out of git. Store server credentials only in local `AGENT.md` or another local secret mechanism.

## Deployment Notes

- The production server uses CentOS.
- Public HTTP entry should avoid ports `80` and `443`; More Offer currently uses port `9001`.
- The backend runs on local port `8080`.
- The Nowcoder MCP server needs a headless browser. On the current server this is EPEL `chromium-headless` at `/usr/lib64/chromium-browser/headless_shell`.
