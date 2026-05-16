package com.moreoffer.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moreoffer.config.NiukeMcpProperties;
import com.moreoffer.dto.InterviewExperienceSummary;
import com.moreoffer.dto.InterviewSearchRequest;
import com.moreoffer.dto.InterviewSearchResponse;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class McpStdioNiukeExperienceGateway implements NiukeExperienceGateway {

    private static final String JSON_RPC_VERSION = "2.0";

    private final NiukeMcpProperties properties;
    private final ObjectMapper objectMapper;

    public McpStdioNiukeExperienceGateway(NiukeMcpProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public InterviewSearchResponse search(InterviewSearchRequest request) {
        if (!StringUtils.hasText(properties.getCommand())) {
            throw new IllegalStateException("Niuke MCP command is not configured");
        }

        List<String> command = new ArrayList<>();
        command.add(properties.getCommand());
        command.addAll(properties.getArgs());

        Process process = null;
        try {
            String query = buildQuery(request);
            process = new ProcessBuilder(command).start();
            try (
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))
            ) {
                send(writer, 1, "initialize", initializeParams());
                readResponse(reader, 1);
                sendNotification(writer, "notifications/initialized");
                send(writer, 2, "tools/call", toolCallParams(request, query));
                JsonNode response = readResponse(reader, 2);
                JsonNode result = response.path("result");
                String text = result.path("content").path(0).path("text").asText();
                if (result.path("isError").asBoolean(false)) {
                    throw new IllegalStateException("Niuke MCP tool failed: " + text);
                }
                return toInterviewSearchResponse(query, text);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to call Niuke MCP server", ex);
        } finally {
            stop(process, properties.getTimeout());
        }
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

    private ObjectNode toolCallParams(InterviewSearchRequest request, String query) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "nowcoder_search");
        ObjectNode arguments = objectMapper.createObjectNode();
        arguments.put("query", query);
        arguments.put("type", "discuss");
        arguments.put("page", request.page());
        arguments.put("response_format", "json");
        params.set("arguments", arguments);
        return params;
    }

    private String buildQuery(InterviewSearchRequest request) {
        List<String> rawTerms = new ArrayList<>();
        rawTerms.add(request.position());
        if (StringUtils.hasText(request.company())) {
            rawTerms.add(request.company());
        }
        rawTerms.addAll(request.keywords());

        List<String> parts = new ArrayList<>();
        parts.addAll(aliases(String.join(" ", rawTerms)));
        for (String term : rawTerms) {
            parts.addAll(aliases(term));
            if (shouldKeepRawTerm(term)) {
                parts.add(term);
            }
        }
        parts.add("\u9762\u7ecf");
        return parts.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .reduce((left, right) -> left + " " + right)
                .orElse(request.position() + " \u9762\u7ecf");
    }

    private boolean shouldKeepRawTerm(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String trimmed = value.trim();
        if (containsCjk(trimmed)) {
            return true;
        }
        String normalized = normalize(trimmed);
        return normalized.equals("java")
                || normalized.equals("spring")
                || normalized.equals("springboot")
                || normalized.equals("springcloud")
                || normalized.equals("redis")
                || normalized.equals("mysql")
                || normalized.equals("jvm")
                || normalized.equals("linux")
                || normalized.equals("kafka")
                || normalized.equals("mybatis");
    }

    private List<String> aliases(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        String normalized = normalize(value);
        List<String> aliases = new ArrayList<>();
        if (normalized.contains("bytedance") || normalized.contains("byte")) {
            aliases.add("\u5b57\u8282\u8df3\u52a8");
        }
        if (normalized.contains("alibaba")) {
            aliases.add("\u963f\u91cc");
        }
        if (normalized.contains("tencent")) {
            aliases.add("\u817e\u8baf");
        }
        if (normalized.contains("backend") || normalized.contains("java")) {
            aliases.add("Java \u540e\u7aef\u5f00\u53d1");
        }
        if (normalized.contains("firstround") || normalized.contains("round1") || normalized.contains("first") || normalized.contains("1st")) {
            aliases.add("\u4e00\u9762");
        }
        if (normalized.contains("secondround") || normalized.contains("round2") || normalized.contains("second") || normalized.contains("2nd")) {
            aliases.add("\u4e8c\u9762");
        }
        if (normalized.contains("finalround") || normalized.contains("final")) {
            aliases.add("\u7ec8\u9762");
        }
        return aliases;
    }

    private String normalize(String value) {
        return value.toLowerCase().replaceAll("[\\s_-]+", "");
    }

    private boolean containsCjk(String value) {
        return value.codePoints().anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private InterviewSearchResponse toInterviewSearchResponse(String query, String text) throws IOException {
        if (!StringUtils.hasText(text) || !text.trim().startsWith("{")) {
            return new InterviewSearchResponse(query, 0, List.of());
        }
        JsonNode root = objectMapper.readTree(text);
        JsonNode itemsNode = root.path("items");
        List<InterviewExperienceSummary> items = new ArrayList<>();
        if (itemsNode.isArray()) {
            int index = 0;
            for (JsonNode itemNode : itemsNode) {
                items.add(new InterviewExperienceSummary(
                        itemNode.path("url").asText("nowcoder-" + index),
                        itemNode.path("title").asText(""),
                        itemNode.path("author").asText("Nowcoder user"),
                        itemNode.path("time").asText(""),
                        itemNode.path("url").asText(""),
                        List.of("Nowcoder", "Interview"),
                        highlights(itemNode.path("summary").asText("")),
                        score(index)
                ));
                index++;
            }
        }
        return new InterviewSearchResponse(query, root.path("count").asInt(items.size()), items);
    }

    private List<String> highlights(String summary) {
        if (!StringUtils.hasText(summary)) {
            return List.of("Nowcoder search result. Open the source link for details.");
        }
        return List.of(summary);
    }

    private double score(int index) {
        return Math.max(0.1, 0.95 - index * 0.03);
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
            JsonNode node = objectMapper.readTree(line);
            if (node.path("id").asInt(-1) == id) {
                if (node.has("error")) {
                    throw new IllegalStateException("Niuke MCP error: " + node.path("error"));
                }
                return node;
            }
        }
        throw new IllegalStateException("Timed out waiting for Niuke MCP response " + id);
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(25);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Niuke MCP response", ex);
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
