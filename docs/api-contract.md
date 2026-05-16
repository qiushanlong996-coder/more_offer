# API 契约

## 搜索牛客面经

`POST /api/interview-experiences/search`

### Request

```json
{
  "position": "Java 后端开发",
  "company": "字节跳动",
  "keywords": ["Spring", "Redis", "一面"],
  "page": 1,
  "size": 10
}
```

### Response

```json
{
  "query": "Java 后端开发 字节跳动 Spring Redis 一面",
  "total": 2,
  "items": [
    {
      "id": "nowcoder-123",
      "title": "字节 Java 后端一面面经",
      "author": "candidate_001",
      "publishedAt": "2026-05-01",
      "sourceUrl": "https://www.nowcoder.com/...",
      "tags": ["Java", "Redis", "一面"],
      "highlights": ["问了 Redis 持久化", "Spring 事务传播机制"],
      "score": 0.91
    }
  ]
}
```

## 获取力扣高频题

`GET /api/leetcode/hot?position=java-backend&limit=20`

### Response

```json
{
  "position": "java-backend",
  "items": [
    {
      "id": "leetcode-146",
      "title": "LRU Cache",
      "difficulty": "Medium",
      "topics": ["Hash Table", "Linked List"],
      "url": "https://leetcode.com/problems/lru-cache/"
    }
  ]
}
```

## 生成准备计划

`POST /api/preparation-plans/generate`

### Request

```json
{
  "position": "Java 后端开发",
  "company": "字节跳动",
  "keywords": ["Spring", "Redis"],
  "days": 5,
  "interviews": [],
  "problems": []
}
```

### Response

```json
{
  "title": "字节跳动 Java 后端开发 5 天冲刺计划",
  "readinessScore": 76,
  "focusAreas": ["Redis 与缓存", "Spring 与事务"],
  "dailyTasks": [
    {
      "day": 1,
      "theme": "Redis 与缓存",
      "goal": "当天把 Redis 与缓存 从“知道”推进到“能讲清楚”。",
      "actions": ["整理 Redis 与缓存 的高频问法，写出 3 个可复述答案。"]
    }
  ],
  "checklist": ["准备 60 秒自我介绍，结尾明确目标岗位匹配点。"],
  "risks": [
    {
      "level": "high",
      "title": "样本偏少",
      "evidence": "当前只有 1 条面经参与计划。",
      "nextAction": "补充搜索公司别名、部门名或轮次关键词。"
    }
  ]
}
```

## Generate Interview Brief

`POST /api/interview-briefs/generate`

### Request

```json
{
  "position": "Java Backend Engineer",
  "company": "ByteDance",
  "keywords": ["Spring", "Redis"],
  "interviews": [],
  "problems": []
}
```

### Response

```json
{
  "title": "ByteDance Java Backend Engineer interview brief",
  "prioritySignals": [
    {
      "level": "high",
      "title": "Spring is the lead signal",
      "evidence": "6 interview notes and 12 coding drills feed this brief.",
      "action": "Prepare one 90-second explanation and one failure case for Spring."
    }
  ],
  "questionClusters": [
    {
      "topic": "Redis",
      "likelyQuestion": "How would you use Redis in a production backend interview scenario?",
      "interviewerLens": "The interviewer is checking trade-offs, failure handling, and whether you can explain impact clearly.",
      "drillSteps": ["Write the core concept in three bullets."]
    }
  ],
  "storyBank": [],
  "followUpQuestions": []
}
```
