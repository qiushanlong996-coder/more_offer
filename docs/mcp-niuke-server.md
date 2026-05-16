# 牛客面经 MCP Server 设计

## 定位

`newcoder-mcp-server-main` 是项目获取牛客网面经的唯一专属 MCP server。它隔离第三方来源访问细节，并向后端提供稳定的结构化工具能力。

## Tool

### `nowcoder_search`

输入：

```json
{
  "query": "Java 后端开发 字节跳动 Spring Redis 面经",
  "type": "discuss",
  "page": 1,
  "response_format": "json"
}
```

输出：

```json
{
  "query": "Java 后端开发 字节跳动 Spring Redis",
  "total": 1,
  "items": [
    {
      "id": "nowcoder-123",
      "title": "字节 Java 后端一面面经",
      "author": "candidate_001",
      "publishedAt": "2026-05-01",
      "sourceUrl": "https://www.nowcoder.com/...",
      "tags": ["Java", "Redis"],
      "highlights": ["Redis 持久化", "Spring 事务"],
      "score": 0.91
    }
  ]
}
```

## 约束

- 不在仓库保存牛客账号、Cookie 或密码。
- 默认只抓取公开可访问页面或经过授权的数据源。
- 返回摘要、标签、链接和短匹配片段，不返回大段原文。
- 对同一关键词做速率限制和缓存。
- 所有结果保留 `sourceUrl`，方便用户回到原站查看。

## 后端集成

后端通过 `NiukeExperienceGateway` 调用 MCP server。当前使用仓库根目录已有的 `newcoder-mcp-server-main`，推荐本地开发使用 stdio 进程：

```yaml
more-offer:
  niuke:
    mcp:
      command: node
      args:
        - ../newcoder-mcp-server-main/dist/index.js
```

生产环境可以替换为独立进程、容器或远程 MCP endpoint，但业务代码仍只依赖网关接口。
