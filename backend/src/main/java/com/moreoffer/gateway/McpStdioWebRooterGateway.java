package com.moreoffer.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moreoffer.config.WebRooterMcpProperties;
import com.moreoffer.gateway.WebRooterMcpGateway.WebRooterArticleCandidate;
import com.moreoffer.gateway.WebRooterMcpGateway.WebRooterSearchResult;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class McpStdioWebRooterGateway implements WebRooterMcpGateway {

    private static final String JSON_RPC_VERSION = "2.0";
    private static final Pattern GITHUB_FULL_NAME_PATTERN = Pattern.compile("\"full_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile("\"description\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern STARS_PATTERN = Pattern.compile("\"stargazers_count\"\\s*:\\s*(\\d+)");
    private static final Pattern LANGUAGE_PATTERN = Pattern.compile("\"language\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);
    private final WebRooterMcpProperties properties;
    private final ObjectMapper objectMapper;

    public McpStdioWebRooterGateway(WebRooterMcpProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public WebRooterSearchResult searchTech(String query, int limit) {
        if (!StringUtils.hasText(properties.getCommand())) {
            throw new IllegalStateException("Web-Rooter MCP command is not configured");
        }

        List<String> command = new ArrayList<>();
        command.add(properties.getCommand());
        command.addAll(properties.getArgs());

        Process process = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            processBuilder.environment().put("PYTHONIOENCODING", "utf-8");
            processBuilder.environment().put("PYTHONUNBUFFERED", "1");
            configureCentosBrowserRuntime(processBuilder);
            process = processBuilder.start();

            try (
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))
            ) {
                send(writer, 1, "initialize", initializeParams());
                readResponse(reader, 1);
                sendNotification(writer, "notifications/initialized");
                return fetchCuratedTechSources(writer, reader, query, limit);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to call Web-Rooter MCP server", ex);
        } finally {
            stop(process, properties.getTimeout());
        }
    }

    private WebRooterSearchResult fetchCuratedTechSources(BufferedWriter writer, BufferedReader reader, String query, int limit) throws IOException {
        int maxItems = Math.max(1, Math.min(limit, 20));
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String chineseQuery = toChineseQuery(query);
        String encodedChineseQuery = URLEncoder.encode(chineseQuery, StandardCharsets.UTF_8);
        Map<String, WebRooterArticleCandidate> deduped = new LinkedHashMap<>();
        int id = 2;

        String csdnUrl = "https://so.csdn.net/api/v3/search?q=" + encodedChineseQuery
                + "&t=blog&p=1&s=0&tm=0&lv=-1&ft=0&l=&u=&ct=-1";
        try {
            String csdnText = callFetch(writer, reader, id++, csdnUrl);
            parseCsdn(csdnText, deduped, Math.min(maxItems, 4));
        } catch (IOException | RuntimeException ignored) {
            // CSDN occasionally returns anti-bot or malformed payloads; keep other Chinese sources.
        }

        String bilibiliUrl = "https://api.bilibili.com/x/web-interface/wbi/search/type?search_type=video&keyword="
                + encodedChineseQuery + "&page=1&order=pubdate";
        try {
            String bilibiliText = callFetch(writer, reader, id++, bilibiliUrl);
            int[] commentFetchId = {id};
            parseBilibili(bilibiliText, deduped, Math.min(maxItems, Math.max(4, maxItems / 2)), writer, reader, commentFetchId);
            id = commentFetchId[0];
        } catch (IOException | RuntimeException ignored) {
            // Bilibili may ask for risk verification; social search remains as a fallback.
        }

        String v2exUrl = "https://www.sov2ex.com/api/search?q=" + encodedChineseQuery;
        try {
            String v2exText = callFetch(writer, reader, id++, v2exUrl);
            parseV2ex(v2exText, deduped, Math.min(maxItems, deduped.size() + 4));
        } catch (IOException | RuntimeException ignored) {
            // V2EX search is supplementary community signal.
        }

        try {
            String socialText = callTool(writer, reader, id++, "web_search_social", socialParams(chineseQuery));
            parseSocialSearch(socialText, deduped, Math.min(maxItems, 12));
        } catch (IOException | RuntimeException ignored) {
            // Social search can be slow or blocked by platform pages; API sources above are the stable path.
        }

        if (deduped.size() < 3) {
            String discussionQuery = URLEncoder.encode(toDiscussionQuery(query), StandardCharsets.UTF_8);
            String hackerNewsUrl = "https://hn.algolia.com/api/v1/search?query=" + discussionQuery
                    + "&tags=story&hitsPerPage=" + maxItems;
            try {
                String hackerNewsText = callFetch(writer, reader, id++, hackerNewsUrl);
                parseHackerNews(query, hackerNewsText, deduped, maxItems);
            } catch (IOException | RuntimeException ignored) {
                // Keep partial results from other sources when one upstream source is malformed or rate-limited.
            }
        }

        if (deduped.isEmpty()) {
            String githubUrl = "https://api.github.com/search/repositories?q=" + encodedQuery
                    + "&sort=stars&order=desc&per_page=" + maxItems;
            try {
                String githubText = callFetch(writer, reader, id, githubUrl);
                parseGitHub(githubText, deduped, maxItems);
            } catch (IOException | RuntimeException ignored) {
                // GitHub is a last resort now; Chinese article/community sources are preferred.
            }
        }

        return new WebRooterSearchResult(chineseQuery, deduped.values().stream().limit(maxItems).toList(), "");
    }

    private String toDiscussionQuery(String query) {
        String simplified = query
                .replace("最新", " ")
                .replace("技术", " ")
                .replace("实践", " ")
                .replace("架构", " ")
                .replaceAll("(?i)\\bbackend\\b", " ")
                .replaceAll("(?i)\\barchitecture\\b", " ")
                .replaceAll("(?i)\\bengineer\\b", " ")
                .replaceAll("(?i)\\bproduction\\b", " ")
                .replaceAll("(?i)\\bcase\\b", " ")
                .replaceAll("(?i)\\bstudy\\b", " ")
                .trim()
                .replaceAll("\\s+", " ");
        return StringUtils.hasText(simplified) ? simplified : query;
    }

    private String toChineseQuery(String query) {
        String normalized = query
                .replaceAll("(?i)\\bbackend\\b", "后端")
                .replaceAll("(?i)\\barchitecture\\b", "架构")
                .replaceAll("(?i)\\bengineer\\b", "工程师")
                .trim()
                .replaceAll("\\s+", " ");
        if (!normalized.contains("最新")) {
            normalized = normalized + " 最新";
        }
        if (!normalized.contains("实践")) {
            normalized = normalized + " 实践";
        }
        return normalized;
    }

    private String callFetch(BufferedWriter writer, BufferedReader reader, int id, String url) throws IOException {
        return callTool(writer, reader, id, "web_fetch", fetchArguments(url));
    }

    private String callTool(BufferedWriter writer, BufferedReader reader, int id, String toolName, ObjectNode arguments) throws IOException {
        send(writer, id, "tools/call", toolParams(toolName, arguments));
        JsonNode response = readResponse(reader, id);
        JsonNode result = response.path("result");
        String text = result.path("content").path(0).path("text").asText();
        if (result.path("isError").asBoolean(false)) {
            throw new IllegalStateException("Web-Rooter MCP fetch failed: " + text);
        }
        return text;
    }

    private void parseCsdn(String text, Map<String, WebRooterArticleCandidate> articles, int limit) throws IOException {
        JsonNode apiRoot = extractFetchedJson(text);
        JsonNode results = apiRoot.path("result_vos");
        if (!results.isArray()) {
            return;
        }

        List<JsonNode> sorted = new ArrayList<>();
        results.forEach(sorted::add);
        sorted.sort(Comparator.comparingLong(item -> -item.path("create_time").asLong(0)));
        for (JsonNode item : sorted) {
            if (articles.size() >= limit) {
                return;
            }
            String title = cleanText(item.path("title").asText(""));
            String url = firstText(item.path("url_location").asText(""), item.path("url").asText(""));
            if (!isUsableTitle(title) || !StringUtils.hasText(url)) {
                continue;
            }
            String snippet = firstText(item.path("description").asText(""), item.path("digest").asText(""));
            String publishedAt = firstText(item.path("created_at").asText(""), item.path("create_time_str").asText(""));
            String metrics = "阅读 " + item.path("view_num").asText(item.path("view").asText("0"))
                    + "，点赞 " + item.path("digg").asText("0")
                    + "，评论 " + item.path("comment").asText("0") + "。";
            articles.putIfAbsent(url, new WebRooterArticleCandidate(
                    title,
                    url,
                    truncate(cleanText(snippet) + " " + metrics, 320),
                    "csdn",
                    publishedAt,
                    articles.size() + 1
            ));
        }
    }

    private void parseBilibili(
            String text,
            Map<String, WebRooterArticleCandidate> articles,
            int limit,
            BufferedWriter writer,
            BufferedReader reader,
            int[] nextId
    ) throws IOException {
        JsonNode apiRoot = extractFetchedJson(text);
        JsonNode results = apiRoot.path("data").path("result");
        if (!results.isArray()) {
            return;
        }

        List<JsonNode> sorted = new ArrayList<>();
        results.forEach(sorted::add);
        sorted.sort(Comparator.comparingLong(item -> -item.path("pubdate").asLong(0)));
        int commentFetches = 0;
        for (JsonNode item : sorted) {
            if (articles.size() >= limit) {
                return;
            }
            String title = cleanText(item.path("title").asText(""));
            String aid = item.path("aid").asText("");
            String bvid = item.path("bvid").asText("");
            String url = StringUtils.hasText(bvid)
                    ? "https://www.bilibili.com/video/" + bvid
                    : firstText(item.path("arcurl").asText(""), item.path("url").asText(""));
            if (!isUsableTitle(title) || !StringUtils.hasText(url) || item.path("is_pay").asInt(0) == 1) {
                continue;
            }
            String commentSummary = "";
            if (commentFetches < 3 && StringUtils.hasText(aid)) {
                commentFetches++;
                commentSummary = fetchBilibiliCommentSummary(writer, reader, nextId, aid, bvid);
            }
            String publishedAt = formatEpochSeconds(item.path("pubdate").asLong(0));
            String snippet = cleanText(firstText(item.path("description").asText(""), item.path("tag").asText("")))
                    + " UP " + item.path("author").asText("未知")
                    + "，播放 " + item.path("play").asText("0")
                    + "，评论 " + item.path("review").asText("0")
                    + "，弹幕 " + item.path("video_review").asText("0") + "。" + commentSummary;
            articles.putIfAbsent(url, new WebRooterArticleCandidate(
                    title,
                    url,
                    truncate(snippet, 320),
                    "bilibili",
                    publishedAt,
                    articles.size() + 1
            ));
        }
    }

    private String fetchBilibiliCommentSummary(
            BufferedWriter writer,
            BufferedReader reader,
            int[] nextId,
            String aid,
            String bvid
    ) {
        try {
            String url = "https://api.bilibili.com/x/v2/reply?oid=" + URLEncoder.encode(aid, StandardCharsets.UTF_8)
                    + "&type=1&pn=1&ps=8&sort=2";
            String text = callFetch(writer, reader, nextId[0]++, url);
            return parseBilibiliCommentSummary(text, bvid);
        } catch (IOException | RuntimeException ignored) {
            return "";
        }
    }

    private String parseBilibiliCommentSummary(String text, String bvid) throws IOException {
        JsonNode apiRoot = extractFetchedJson(text);
        JsonNode replies = apiRoot.path("data").path("replies");
        if (!replies.isArray() || replies.isEmpty()) {
            return "";
        }
        List<String> comments = new ArrayList<>();
        for (JsonNode reply : replies) {
            if (comments.size() >= 3) {
                break;
            }
            String message = cleanText(reply.path("content").path("message").asText(""));
            if (!StringUtils.hasText(message)) {
                continue;
            }
            String author = cleanText(reply.path("member").path("uname").asText(""));
            int likeCount = reply.path("like").asInt(0);
            String prefix = StringUtils.hasText(author) ? author + ": " : "";
            String suffix = likeCount > 0 ? " (赞 " + likeCount + ")" : "";
            comments.add(prefix + truncate(message, 80) + suffix);
        }
        if (comments.isEmpty()) {
            return "";
        }
        String source = StringUtils.hasText(bvid) ? " BVID " + bvid : "";
        return " 热评" + source + ": " + String.join(" / ", comments);
    }

    private void parseV2ex(String text, Map<String, WebRooterArticleCandidate> articles, int limit) throws IOException {
        JsonNode apiRoot = extractFetchedJson(text);
        JsonNode hits = apiRoot.path("hits");
        if (!hits.isArray()) {
            return;
        }

        List<JsonNode> sorted = new ArrayList<>();
        hits.forEach(sorted::add);
        sorted.sort(Comparator.comparing(hit -> hit.path("_source").path("created").asText(""), Comparator.reverseOrder()));
        for (JsonNode hit : sorted) {
            if (articles.size() >= limit) {
                return;
            }
            JsonNode source = hit.path("_source");
            String title = cleanText(source.path("title").asText(""));
            String topicId = source.path("id").asText("");
            if (!isUsableTitle(title) || !StringUtils.hasText(topicId)) {
                continue;
            }
            String url = "https://www.v2ex.com/t/" + topicId;
            String snippet = cleanText(source.path("content").asText(""));
            String replies = source.path("replies").asText("");
            if (StringUtils.hasText(replies)) {
                snippet = snippet + " 回复 " + replies + "。";
            }
            articles.putIfAbsent(url, new WebRooterArticleCandidate(
                    title,
                    url,
                    truncate(snippet, 320),
                    "v2ex",
                    source.path("created").asText(""),
                    articles.size() + 1
            ));
        }
    }

    private void parseSocialSearch(String text, Map<String, WebRooterArticleCandidate> articles, int limit) throws IOException {
        if (!StringUtils.hasText(text) || !text.trim().startsWith("{")) {
            return;
        }
        JsonNode root = objectMapper.readTree(text);
        JsonNode results = root.path("results");
        if (!results.isArray()) {
            results = root.path("citations");
        }
        if (!results.isArray()) {
            return;
        }

        for (JsonNode item : results) {
            if (articles.size() >= limit) {
                return;
            }
            String title = cleanText(item.path("title").asText(""));
            String url = item.path("url").asText("");
            if (!isUsableTitle(title) || !StringUtils.hasText(url)) {
                continue;
            }
            String engine = firstText(item.path("engine").asText(""), item.path("domain").asText(""));
            articles.putIfAbsent(url, new WebRooterArticleCandidate(
                    title,
                    url,
                    truncate(cleanText(item.path("snippet").asText("")), 280),
                    StringUtils.hasText(engine) ? engine.toLowerCase(Locale.ROOT) : "social",
                    item.path("retrieved_at").asText(""),
                    articles.size() + 1
            ));
        }
    }

    private void parseHackerNews(String query, String text, Map<String, WebRooterArticleCandidate> articles, int limit) throws IOException {
        JsonNode apiRoot = extractFetchedJson(text);
        JsonNode hits = apiRoot.path("hits");
        if (!hits.isArray()) {
            return;
        }

        for (JsonNode hit : hits) {
            if (articles.size() >= limit) {
                return;
            }
            String title = firstText(hit.path("title").asText(""), hit.path("story_title").asText(""));
            String url = firstText(hit.path("url").asText(""), hit.path("story_url").asText(""));
            String objectId = hit.path("objectID").asText("");
            if (!StringUtils.hasText(url) && StringUtils.hasText(objectId)) {
                url = "https://news.ycombinator.com/item?id=" + objectId;
            }
            if (!StringUtils.hasText(title) || !StringUtils.hasText(url)) {
                continue;
            }
            String snippet = "Hacker News discussion for " + query
                    + "; points " + hit.path("points").asInt(0)
                    + ", comments " + hit.path("num_comments").asInt(0) + ".";
            articles.putIfAbsent(url, new WebRooterArticleCandidate(
                    title,
                    url,
                    snippet,
                    "hackernews",
                    hit.path("created_at").asText(""),
                    articles.size() + 1
            ));
        }
    }

    private void parseGitHub(String text, Map<String, WebRooterArticleCandidate> articles, int limit) throws IOException {
        JsonNode apiRoot = extractFetchedJson(text);
        JsonNode items = apiRoot.path("items");
        if (items.isArray()) {
            for (JsonNode item : items) {
                if (articles.size() >= limit) {
                    return;
                }
                String title = item.path("full_name").asText("");
                String url = item.path("html_url").asText("");
                if (!StringUtils.hasText(title) || !StringUtils.hasText(url)) {
                    continue;
                }
                String description = item.path("description").asText("");
                String language = item.path("language").asText("unknown");
                String snippet = (StringUtils.hasText(description) ? description + " " : "")
                        + "GitHub stars " + item.path("stargazers_count").asInt(0)
                        + ", language " + language + ".";
                articles.putIfAbsent(url, new WebRooterArticleCandidate(
                        title,
                        url,
                        snippet,
                        "github",
                        item.path("pushed_at").asText(item.path("updated_at").asText("")),
                        articles.size() + 1
                ));
            }
            return;
        }

        parseGitHubExtractedText(extractFetchedText(text), articles, limit);
    }

    private void parseGitHubExtractedText(String rawText, Map<String, WebRooterArticleCandidate> articles, int limit) throws IOException {
        if (!StringUtils.hasText(rawText)) {
            return;
        }

        Matcher matcher = GITHUB_FULL_NAME_PATTERN.matcher(rawText);
        while (matcher.find() && articles.size() < limit) {
            String fullName = matcher.group(1);
            String repoUrl = "https://github.com/" + fullName;
            int end = Math.min(rawText.length(), matcher.start() + 9000);
            String window = rawText.substring(matcher.start(), end);
            if (!window.contains("\"html_url\": \"" + repoUrl + "\"")) {
                continue;
            }
            String description = matchFirst(DESCRIPTION_PATTERN, window);
            String stars = matchFirst(STARS_PATTERN, window);
            String language = matchFirst(LANGUAGE_PATTERN, window);
            String snippet = (StringUtils.hasText(description) ? unescapeJsonString(description) + " " : "")
                    + "GitHub stars " + (StringUtils.hasText(stars) ? stars : "0")
                    + ", language " + (StringUtils.hasText(language) ? language : "unknown") + ".";
            articles.putIfAbsent(repoUrl, new WebRooterArticleCandidate(fullName, repoUrl, snippet, "github", "", articles.size() + 1));
        }
    }

    private JsonNode extractFetchedJson(String text) throws IOException {
        if (!StringUtils.hasText(text) || !text.trim().startsWith("{")) {
            return objectMapper.createObjectNode();
        }
        JsonNode root = objectMapper.readTree(text);
        if (root.hasNonNull("error")) {
            throw new IllegalStateException("Web-Rooter MCP fetch error: " + root.path("error").asText());
        }
        String raw = root.path("data").path("text").asText("");
        if (!StringUtils.hasText(raw) || !raw.trim().startsWith("{")) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(raw);
    }

    private String extractFetchedText(String text) throws IOException {
        if (!StringUtils.hasText(text) || !text.trim().startsWith("{")) {
            return "";
        }
        JsonNode root = objectMapper.readTree(text);
        return root.path("data").path("text").asText("");
    }

    private String matchFirst(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String unescapeJsonString(String value) throws IOException {
        try {
            return objectMapper.readTree("\"" + value + "\"").asText();
        } catch (IOException ex) {
            return value.replace("\\n", " ").replace("\\\"", "\"").replace("\\/", "/");
        }
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private ObjectNode initializeParams() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", "2024-11-05");
        params.set("capabilities", objectMapper.createObjectNode());
        ObjectNode clientInfo = objectMapper.createObjectNode();
        clientInfo.put("name", "more-offer-backend");
        clientInfo.put("version", "0.1.0");
        params.set("clientInfo", clientInfo);
        return params;
    }

    private void configureCentosBrowserRuntime(ProcessBuilder processBuilder) {
        Path nodePath = Path.of("/opt/more-offer/runtime/node/bin/node");
        Path chromiumPath = Path.of("/usr/lib64/chromium-browser/headless_shell");
        if (Files.isExecutable(nodePath)) {
            processBuilder.environment().putIfAbsent("PLAYWRIGHT_NODEJS_PATH", nodePath.toString());
        }
        if (Files.isExecutable(chromiumPath)) {
            processBuilder.environment().putIfAbsent("WEB_ROOTER_USE_REAL_CHROME", "true");
            processBuilder.environment().putIfAbsent("WEB_ROOTER_CHROME_PATH", chromiumPath.toString());
        }
    }

    private ObjectNode toolParams(String name, ObjectNode arguments) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", name);
        params.set("arguments", arguments);
        return params;
    }

    private ObjectNode fetchArguments(String url) {
        ObjectNode arguments = objectMapper.createObjectNode();
        arguments.put("url", url);
        return arguments;
    }

    private ObjectNode socialParams(String query) {
        ObjectNode arguments = objectMapper.createObjectNode();
        arguments.put("query", query);
        arguments.putArray("platforms")
                .add("bilibili")
                .add("zhihu")
                .add("weibo");
        return arguments;
    }

    private boolean isUsableTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return false;
        }
        String normalized = title.trim();
        if (normalized.length() < 4) {
            return false;
        }
        int letters = 0;
        for (int index = 0; index < normalized.length(); index++) {
            if (Character.isLetter(normalized.charAt(index))) {
                letters++;
            }
        }
        return letters >= 2;
    }

    private String cleanText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String withoutTags = HTML_TAG_PATTERN.matcher(value).replaceAll("");
        return withoutTags
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return StringUtils.hasText(value) ? value : "";
        }
        return value.substring(0, maxLength - 1) + "…";
    }

    private String formatEpochSeconds(long epochSeconds) {
        if (epochSeconds <= 0) {
            return "";
        }
        return DAY_FORMATTER.format(Instant.ofEpochSecond(epochSeconds));
    }

    private WebRooterSearchResult toSearchResult(String fallbackQuery, String text, int limit) throws IOException {
        if (!StringUtils.hasText(text) || !text.trim().startsWith("{")) {
            return new WebRooterSearchResult(fallbackQuery, List.of(), "");
        }

        JsonNode root = objectMapper.readTree(text);
        if (root.hasNonNull("error")) {
            throw new IllegalStateException("Web-Rooter MCP error: " + root.path("error").asText());
        }

        String query = root.path("query").asText(fallbackQuery);
        String referencesText = root.path("references_text").asText("");
        List<WebRooterArticleCandidate> articles = new ArrayList<>();
        JsonNode results = root.path("results");
        if (results.isArray()) {
            int maxItems = Math.max(1, Math.min(limit, 20));
            for (JsonNode item : results) {
                if (articles.size() >= maxItems) {
                    break;
                }
                String title = item.path("title").asText("");
                String url = item.path("url").asText("");
                if (!StringUtils.hasText(title) || !StringUtils.hasText(url)) {
                    continue;
                }
                articles.add(new WebRooterArticleCandidate(
                        title,
                        url,
                        item.path("snippet").asText(""),
                        item.path("engine").asText("web-rooter"),
                        item.path("published_at").asText(""),
                        item.path("rank").asInt(articles.size() + 1)
                ));
            }
        }
        return new WebRooterSearchResult(query, articles, referencesText);
    }

    private void send(BufferedWriter writer, int id, String method, JsonNode params) throws IOException {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("jsonrpc", JSON_RPC_VERSION);
        message.put("id", id);
        message.put("method", method);
        message.set("params", params);
        writer.write(objectMapper.writeValueAsString(message));
        writer.newLine();
        writer.flush();
    }

    private void sendNotification(BufferedWriter writer, String method) throws IOException {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("jsonrpc", JSON_RPC_VERSION);
        message.put("method", method);
        writer.write(objectMapper.writeValueAsString(message));
        writer.newLine();
        writer.flush();
    }

    private JsonNode readResponse(BufferedReader reader, int id) throws IOException {
        long deadline = System.nanoTime() + properties.getTimeout().toNanos();
        while (System.nanoTime() < deadline) {
            if (!reader.ready()) {
                sleepBriefly();
                continue;
            }

            String line = reader.readLine();
            if (line == null) {
                break;
            }
            String trimmed = line.trim();
            if (!trimmed.startsWith("{")) {
                continue;
            }
            JsonNode node = objectMapper.readTree(trimmed);
            if (node.path("id").asInt(-1) == id) {
                if (node.has("error")) {
                    throw new IllegalStateException("Web-Rooter MCP error: " + node.path("error"));
                }
                return node;
            }
        }
        throw new IllegalStateException("Timed out waiting for Web-Rooter MCP response " + id);
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(25);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Web-Rooter MCP response", ex);
        }
    }

    private void stop(Process process, Duration timeout) {
        if (process == null) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
