package com.moreoffer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moreoffer.config.OpenAiProperties;
import com.moreoffer.dto.TechRadarArticle;
import com.moreoffer.dto.TechRadarRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OpenAiSummaryService {

    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public OpenAiSummaryService(OpenAiProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    OpenAiSummaryService(OpenAiProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public SummaryResult summarize(TechRadarRequest request, String query, List<TechRadarArticle> articles) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            return new SummaryResult(fallbackSummary(request, query, articles), false);
        }

        try {
            String prompt = buildPrompt(request, query, articles);
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", properties.getModel());
            body.put("max_output_tokens", 700);
            ArrayNode input = body.putArray("input");
            ObjectNode system = input.addObject();
            system.put("role", "system");
            system.put("content", "你是求职面试产品里的技术研究分析师。请只根据用户给出的搜索结果总结，不编造来源。");
            ObjectNode user = input.addObject();
            user.put("role", "user");
            user.put("content", prompt);

            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(properties.getEndpoint()))
                    .timeout(properties.getTimeout())
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new SummaryResult(fallbackSummary(request, query, articles), false);
            }

            String output = extractResponseText(response.body());
            if (!StringUtils.hasText(output)) {
                return new SummaryResult(fallbackSummary(request, query, articles), false);
            }
            return new SummaryResult(output.trim(), true);
        } catch (IOException ex) {
            return new SummaryResult(fallbackSummary(request, query, articles), false);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new SummaryResult(fallbackSummary(request, query, articles), false);
        } catch (RuntimeException ex) {
            return new SummaryResult(fallbackSummary(request, query, articles), false);
        }
    }

    private String buildPrompt(TechRadarRequest request, String query, List<TechRadarArticle> articles) throws IOException {
        ObjectNode context = objectMapper.createObjectNode();
        context.put("query", query);
        context.put("position", request.position());
        context.put("company", request.company());
        ArrayNode keywords = context.putArray("keywords");
        request.keywords().forEach(keywords::add);
        ArrayNode articleNodes = context.putArray("articles");
        for (TechRadarArticle article : articles) {
            ObjectNode node = articleNodes.addObject();
            node.put("title", article.title());
            node.put("source", article.source());
            node.put("url", article.sourceUrl());
            node.put("snippet", article.snippet());
            node.put("publishedAt", article.publishedAt());
        }
        return """
                请基于下面 JSON 生成中文摘要，控制在 5 句话内：
                1. 先说明这些中文技术文章、视频或社区讨论共同指向的趋势。
                2. 按来源差异提炼 2-3 个技术追问方向，优先引用国内开发者语境。
                3. 不要输出 Markdown 表格，不要虚构文章中没有的信息。

                JSON:
                """ + objectMapper.writeValueAsString(context);
    }

    private String extractResponseText(String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        if (root.hasNonNull("output_text")) {
            return root.path("output_text").asText();
        }
        StringBuilder builder = new StringBuilder();
        JsonNode output = root.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (!content.isArray()) {
                    continue;
                }
                for (JsonNode contentItem : content) {
                    if (contentItem.hasNonNull("text")) {
                        builder.append(contentItem.path("text").asText()).append('\n');
                    }
                }
            }
        }
        return builder.toString().trim();
    }

    private String fallbackSummary(TechRadarRequest request, String query, List<TechRadarArticle> articles) {
        if (articles.isEmpty()) {
            return "Web-Rooter 还没有返回可用文章。当前建议先把 \"" + query + "\" 拆成框架、缓存、可观测性和项目落地四类问题继续检索。";
        }
        String company = StringUtils.hasText(request.company()) ? request.company() : "目标公司";
        String leadTitle = articles.getFirst().title();
        int count = articles.size();
        long sourceCount = articles.stream().map(TechRadarArticle::source).distinct().count();
        return "本次 Web-Rooter 搜索抓到 " + count + " 条中文优先技术材料，覆盖 " + sourceCount
                + " 类来源，首要信号来自《" + leadTitle
                + "》。这些材料适合作为 " + company + " " + request.position()
                + " 面试的补充来源：把文章、视频和评论讨论里的工程取舍、失败场景和性能指标整理成追问链，再回到面经里验证是否高频出现。";
    }

    public record SummaryResult(String text, boolean generatedByOpenAi) {
    }
}
