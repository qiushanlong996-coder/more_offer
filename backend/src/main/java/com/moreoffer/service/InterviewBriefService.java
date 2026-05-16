package com.moreoffer.service;

import com.moreoffer.dto.InterviewBriefRequest;
import com.moreoffer.dto.InterviewBriefResponse;
import com.moreoffer.dto.InterviewBriefResponse.PrioritySignal;
import com.moreoffer.dto.InterviewBriefResponse.QuestionCluster;
import com.moreoffer.dto.InterviewBriefResponse.StoryPrompt;
import com.moreoffer.dto.InterviewExperienceSummary;
import com.moreoffer.dto.LeetCodeProblem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class InterviewBriefService {

    private static final List<String> FALLBACK_TOPICS = List.of(
            "Java concurrency",
            "Spring Boot internals",
            "Redis caching",
            "MySQL transactions"
    );

    public InterviewBriefResponse generate(InterviewBriefRequest request) {
        List<String> topics = topTopics(request);
        List<PrioritySignal> signals = buildSignals(request, topics);
        List<QuestionCluster> clusters = buildClusters(request, topics);
        List<StoryPrompt> storyBank = buildStoryBank(request, topics);
        List<String> followUps = buildFollowUps(request, topics);

        String company = blankToDefault(request.company(), "Target company");
        String title = company + " " + request.position() + " interview brief";
        return new InterviewBriefResponse(title, signals, clusters, storyBank, followUps);
    }

    private List<String> topTopics(InterviewBriefRequest request) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Stream.concat(
                        request.keywords().stream(),
                        Stream.concat(
                                request.interviews().stream().flatMap(item -> item.tags().stream()),
                                request.problems().stream().flatMap(problem -> problem.topics().stream())
                        )
                )
                .map(this::cleanTopic)
                .filter(topic -> !topic.isBlank())
                .forEach(topic -> counts.merge(topic, 1, Integer::sum));

        List<String> topics = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .limit(5)
                .toList();

        if (topics.size() >= 3) {
            return topics;
        }

        List<String> merged = new ArrayList<>(topics);
        FALLBACK_TOPICS.stream()
                .filter(topic -> !merged.contains(topic))
                .limit(5 - merged.size())
                .forEach(merged::add);
        return merged;
    }

    private List<PrioritySignal> buildSignals(InterviewBriefRequest request, List<String> topics) {
        int interviewCount = request.interviews().size();
        int hardProblems = (int) request.problems().stream()
                .filter(problem -> "hard".equalsIgnoreCase(problem.difficulty()))
                .count();

        List<PrioritySignal> signals = new ArrayList<>();
        signals.add(new PrioritySignal(
                interviewCount >= 5 ? "high" : "medium",
                topics.get(0) + " is the lead signal",
                interviewCount + " interview notes and " + request.problems().size() + " coding drills feed this brief.",
                "Prepare one 90-second explanation and one failure case for " + topics.get(0) + "."
        ));
        signals.add(new PrioritySignal(
                hardProblems >= 2 ? "high" : "medium",
                "Coding bar needs deliberate practice",
                hardProblems + " hard problems are in the active set.",
                "Pair each system topic with one timed coding drill before review."
        ));
        signals.add(new PrioritySignal(
                request.keywords().isEmpty() ? "medium" : "low",
                "Keyword coverage is explicit",
                "Current search keywords: " + (request.keywords().isEmpty() ? "none" : String.join(", ", request.keywords())) + ".",
                "Keep the next search narrow when a question cluster feels vague."
        ));
        return signals;
    }

    private List<QuestionCluster> buildClusters(InterviewBriefRequest request, List<String> topics) {
        List<String> problemTitles = request.problems().stream()
                .map(LeetCodeProblem::title)
                .filter(Objects::nonNull)
                .limit(3)
                .toList();
        String drill = problemTitles.isEmpty() ? "Run a 30-minute whiteboard drill." : "Drill: " + String.join(", ", problemTitles) + ".";

        return topics.stream()
                .limit(4)
                .map(topic -> new QuestionCluster(
                        topic,
                        "How would you use " + topic + " in a production backend interview scenario?",
                        "The interviewer is checking trade-offs, failure handling, and whether you can explain impact clearly.",
                        List.of(
                                "Write the core concept in three bullets.",
                                "Add one bottleneck and one mitigation.",
                                drill
                        )
                ))
                .toList();
    }

    private List<StoryPrompt> buildStoryBank(InterviewBriefRequest request, List<String> topics) {
        String company = blankToDefault(request.company(), "the company");
        return List.of(
                new StoryPrompt(
                        "Ownership",
                        "Prepare a story where you noticed a backend reliability risk before it became visible to users.",
                        List.of("baseline metric", "decision you made", "measured result")
                ),
                new StoryPrompt(
                        "Trade-off",
                        "Prepare a " + company + " style answer comparing speed, correctness, and long-term maintainability.",
                        List.of(topics.get(0), "alternative considered", "why the final choice won")
                ),
                new StoryPrompt(
                        "Debugging",
                        "Prepare a story where logs, metrics, or traces changed your first hypothesis.",
                        List.of("initial symptom", "instrumentation used", "final root cause")
                )
        );
    }

    private List<String> buildFollowUps(InterviewBriefRequest request, List<String> topics) {
        String company = blankToDefault(request.company(), "this team");
        return List.of(
                "What does strong " + request.position() + " performance look like in the first 90 days at " + company + "?",
                "Which part of the stack creates the most interview signal for this role?",
                "How does the team balance delivery speed with quality for " + topics.get(0) + " related work?",
                "What are the common reasons candidates fail the final technical discussion?"
        );
    }

    private String cleanTopic(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isBlank()) {
            return "";
        }
        if (trimmed.length() <= 3) {
            return trimmed.toUpperCase(Locale.ROOT);
        }
        return trimmed.substring(0, 1).toUpperCase(Locale.ROOT) + trimmed.substring(1);
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
