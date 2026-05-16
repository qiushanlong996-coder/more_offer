package com.moreoffer.service;

import com.moreoffer.dto.TechRadarArticle;
import com.moreoffer.dto.TechRadarRequest;
import com.moreoffer.dto.TechRadarResponse;
import com.moreoffer.gateway.WebRooterMcpGateway;
import com.moreoffer.gateway.WebRooterMcpGateway.WebRooterArticleCandidate;
import com.moreoffer.gateway.WebRooterMcpGateway.WebRooterSearchResult;
import com.moreoffer.service.OpenAiSummaryService.SummaryResult;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TechRadarService {

    private static final List<String> FALLBACK_THEMES = List.of(
            "Production trade-offs",
            "Failure handling",
            "Observability",
            "Performance tuning"
    );

    private final WebRooterMcpGateway webRooterMcpGateway;
    private final OpenAiSummaryService openAiSummaryService;

    public TechRadarService(WebRooterMcpGateway webRooterMcpGateway, OpenAiSummaryService openAiSummaryService) {
        this.webRooterMcpGateway = webRooterMcpGateway;
        this.openAiSummaryService = openAiSummaryService;
    }

    public TechRadarResponse research(TechRadarRequest request) {
        String query = buildQuery(request);
        WebRooterSearchResult searchResult;
        try {
            searchResult = webRooterMcpGateway.searchTech(query, request.limit());
        } catch (RuntimeException ex) {
            searchResult = new WebRooterSearchResult(query, List.of(), "");
        }

        List<TechRadarArticle> articles = toArticles(searchResult.articles(), request.limit());
        SummaryResult summary = openAiSummaryService.summarize(request, searchResult.query(), articles);
        List<String> themes = buildThemes(request, articles);
        List<String> interviewSignals = buildInterviewSignals(request, themes, articles);
        String source = articles.isEmpty() ? "web-rooter-mcp-empty" : "web-rooter-mcp";

        return new TechRadarResponse(
                searchResult.query(),
                summary.text(),
                summary.generatedByOpenAi(),
                themes,
                articles,
                interviewSignals,
                source,
                OffsetDateTime.now(ZoneOffset.UTC).toString()
        );
    }

    private String buildQuery(TechRadarRequest request) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(request.topic())) {
            parts.add(request.topic());
        } else {
            parts.add(normalizePositionForTechSearch(request.position()));
            request.keywords().stream()
                    .filter(keyword -> !isInterviewRoundKeyword(keyword))
                    .forEach(parts::add);
        }
        parts.add("backend architecture");
        return parts.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .reduce((left, right) -> left + " " + right)
                .orElse("backend architecture");
    }

    private String normalizePositionForTechSearch(String position) {
        if (!StringUtils.hasText(position)) {
            return "backend";
        }
        String lower = position.toLowerCase(Locale.ROOT);
        List<String> tokens = new ArrayList<>();
        if (lower.contains("java")) {
            tokens.add("Java");
        }
        if (lower.contains("go") || lower.contains("golang")) {
            tokens.add("Go");
        }
        if (lower.contains("python")) {
            tokens.add("Python");
        }
        if (lower.contains("backend") || lower.contains("后端")) {
            tokens.add("backend");
        }
        if (tokens.isEmpty()) {
            tokens.add(position.trim().replace("Engineer", "").replace("工程师", "").replace("开发", "").trim());
        }
        return String.join(" ", tokens);
    }

    private boolean isInterviewRoundKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String lower = keyword.toLowerCase(Locale.ROOT);
        return lower.contains("round")
                || lower.contains("interview")
                || lower.contains("一面")
                || lower.contains("二面")
                || lower.contains("三面")
                || lower.contains("终面");
    }

    private List<TechRadarArticle> toArticles(List<WebRooterArticleCandidate> candidates, int limit) {
        List<TechRadarArticle> articles = new ArrayList<>();
        int maxItems = Math.max(1, Math.min(limit, 20));
        for (WebRooterArticleCandidate candidate : candidates) {
            if (articles.size() >= maxItems) {
                break;
            }
            int index = articles.size();
            articles.add(new TechRadarArticle(
                    stableId(candidate.url(), index),
                    candidate.title(),
                    candidate.url(),
                    normalizeSource(candidate.engine(), candidate.url()),
                    candidate.snippet(),
                    "",
                    Math.max(0.1, 0.94 - index * 0.04)
            ));
        }
        return articles;
    }

    private String stableId(String url, int index) {
        return StringUtils.hasText(url) ? "web-rooter-" + Integer.toHexString(url.hashCode()) : "web-rooter-" + index;
    }

    private String normalizeSource(String engine, String url) {
        String value = StringUtils.hasText(engine) ? engine : url;
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("github")) {
            return "GitHub";
        }
        if (lower.contains("stackoverflow")) {
            return "Stack Overflow";
        }
        if (lower.contains("medium")) {
            return "Medium";
        }
        if (lower.contains("hacker") || lower.contains("ycombinator")) {
            return "Hacker News";
        }
        return "Web-Rooter";
    }

    private List<String> buildThemes(TechRadarRequest request, List<TechRadarArticle> articles) {
        Set<String> themes = new LinkedHashSet<>();
        request.keywords().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .limit(4)
                .forEach(themes::add);

        String haystack = articles.stream()
                .map(article -> article.title() + " " + article.snippet())
                .reduce("", (left, right) -> left + " " + right)
                .toLowerCase(Locale.ROOT);

        addThemeIfSeen(themes, haystack, "agent", "LLM agents");
        addThemeIfSeen(themes, haystack, "rag", "RAG systems");
        addThemeIfSeen(themes, haystack, "redis", "Redis caching");
        addThemeIfSeen(themes, haystack, "spring", "Spring Boot");
        addThemeIfSeen(themes, haystack, "kafka", "Kafka messaging");
        addThemeIfSeen(themes, haystack, "observability", "Observability");
        addThemeIfSeen(themes, haystack, "performance", "Performance tuning");

        FALLBACK_THEMES.stream()
                .filter(theme -> !themes.contains(theme))
                .limit(Math.max(0, 5 - themes.size()))
                .forEach(themes::add);
        return themes.stream().limit(6).toList();
    }

    private void addThemeIfSeen(Set<String> themes, String haystack, String needle, String theme) {
        if (haystack.contains(needle)) {
            themes.add(theme);
        }
    }

    private List<String> buildInterviewSignals(TechRadarRequest request, List<String> themes, List<TechRadarArticle> articles) {
        String company = StringUtils.hasText(request.company()) ? request.company() : "目标公司";
        String leadTheme = themes.isEmpty() ? "production trade-offs" : themes.getFirst();
        List<String> signals = new ArrayList<>();
        signals.add("把 " + leadTheme + " 拆成“为什么选它、什么时候不用、出故障怎么定位”三段回答。");
        signals.add("用最新技术文章补足面经缺口：面经告诉你高频题，文章负责提供真实工程案例和指标语言。");
        signals.add("面向 " + company + " 的追问准备一条项目链路：背景、瓶颈、方案、观测指标、复盘改进。");
        if (!articles.isEmpty()) {
            signals.add("优先精读《" + articles.getFirst().title() + "》，把其中的 trade-off 改写成 3 个面试官追问。");
        }
        return signals;
    }
}
