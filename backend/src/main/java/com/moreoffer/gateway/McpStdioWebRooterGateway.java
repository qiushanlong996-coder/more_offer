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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
        String discussionQuery = URLEncoder.encode(toDiscussionQuery(query), StandardCharsets.UTF_8);
        Map<String, WebRooterArticleCandidate> deduped = new LinkedHashMap<>();

        String hackerNewsUrl = "https://hn.algolia.com/api/v1/search?query=" + discussionQuery
                + "&tags=story&hitsPerPage=" + maxItems;
        try {
            String hackerNewsText = callFetch(writer, reader, 2, hackerNewsUrl);
            parseHackerNews(query, hackerNewsText, deduped, maxItems);
        } catch (IOException | RuntimeException ignored) {
            // Keep partial results from other sources when one upstream source is malformed or rate-limited.
        }

        String githubUrl = "https://api.github.com/search/repositories?q=" + encodedQuery
                + "&sort=stars&order=desc&per_page=" + maxItems;
        try {
            String githubText = callFetch(writer, reader, 3, githubUrl);
            parseGitHub(githubText, deduped, maxItems);
        } catch (IOException | RuntimeException ignored) {
            // GitHub API payloads are sometimes transformed by Web-Rooter; skip rather than failing the whole radar.
        }

        return new WebRooterSearchResult(query, deduped.values().stream().limit(maxItems).toList(), "");
    }

    private String toDiscussionQuery(String query) {
        String simplified = query
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

    private String callFetch(BufferedWriter writer, BufferedReader reader, int id, String url) throws IOException {
        send(writer, id, "tools/call", fetchParams(url));
        JsonNode response = readResponse(reader, id);
        JsonNode result = response.path("result");
        String text = result.path("content").path(0).path("text").asText();
        if (result.path("isError").asBoolean(false)) {
            throw new IllegalStateException("Web-Rooter MCP fetch failed: " + text);
        }
        return text;
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
            articles.putIfAbsent(url, new WebRooterArticleCandidate(title, url, snippet, "hackernews", articles.size() + 1));
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
                articles.putIfAbsent(url, new WebRooterArticleCandidate(title, url, snippet, "github", articles.size() + 1));
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
            articles.putIfAbsent(repoUrl, new WebRooterArticleCandidate(fullName, repoUrl, snippet, "github", articles.size() + 1));
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

    private ObjectNode fetchParams(String url) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "web_fetch");
        ObjectNode arguments = objectMapper.createObjectNode();
        arguments.put("url", url);
        params.set("arguments", arguments);
        return params;
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
