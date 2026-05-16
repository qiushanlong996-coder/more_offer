package com.moreoffer.service;

import com.moreoffer.dto.InterviewExperienceSummary;
import com.moreoffer.dto.LeetCodeProblem;
import com.moreoffer.dto.PreparationPlanRequest;
import com.moreoffer.dto.PreparationPlanResponse;
import com.moreoffer.dto.PreparationPlanResponse.PreparationTask;
import com.moreoffer.dto.PreparationPlanResponse.RiskNote;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PreparationPlanService {

    private static final List<String> DEFAULT_AREAS = List.of(
            "Project review",
            "Coding drills",
            "System design",
            "Java fundamentals",
            "Communication"
    );

    public PreparationPlanResponse generate(PreparationPlanRequest request) {
        List<String> focusAreas = extractFocusAreas(request);
        List<PreparationTask> tasks = buildTasks(request, focusAreas);
        List<RiskNote> risks = buildRisks(request, focusAreas);
        List<String> checklist = buildChecklist(request, focusAreas);
        int readinessScore = calculateReadinessScore(request, focusAreas);
        String title = buildTitle(request);

        return new PreparationPlanResponse(title, readinessScore, focusAreas, tasks, checklist, risks);
    }

    private String buildTitle(PreparationPlanRequest request) {
        String company = request.company().isBlank() ? "Target company" : request.company();
        return company + " " + request.position() + " " + request.days() + "-day sprint plan";
    }

    private List<String> extractFocusAreas(PreparationPlanRequest request) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        request.keywords().forEach(keyword -> addKeyword(counts, keyword, 2));

        for (InterviewExperienceSummary interview : request.interviews()) {
            interview.tags().forEach(tag -> addKeyword(counts, tag, 2));
            interview.highlights().forEach(highlight -> inferAreasFromText(counts, highlight));
            inferAreasFromText(counts, interview.title());
        }

        for (LeetCodeProblem problem : request.problems()) {
            problem.topics().forEach(topic -> addKeyword(counts, normalizeTopic(topic), 1));
            addKeyword(counts, problem.difficulty() + " problems", "Hard".equalsIgnoreCase(problem.difficulty()) ? 2 : 1);
        }

        if (counts.isEmpty()) {
            DEFAULT_AREAS.forEach(area -> addKeyword(counts, area, 1));
        }

        return counts.entrySet().stream()
                .sorted((left, right) -> Integer.compare(right.getValue(), left.getValue()))
                .map(Map.Entry::getKey)
                .distinct()
                .limit(6)
                .toList();
    }

    private void inferAreasFromText(Map<String, Integer> counts, String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (lower.contains("redis") || lower.contains("cache")) {
            addKeyword(counts, "Redis and caching", 3);
        }
        if (lower.contains("spring") || lower.contains("transaction") || lower.contains("bean")) {
            addKeyword(counts, "Spring transactions", 3);
        }
        if (lower.contains("mysql") || lower.contains("sql") || lower.contains("index")) {
            addKeyword(counts, "MySQL indexing", 3);
        }
        if (lower.contains("mq") || lower.contains("kafka") || lower.contains("message")) {
            addKeyword(counts, "Message queues", 2);
        }
        if (lower.contains("jvm") || lower.contains("gc")) {
            addKeyword(counts, "JVM and GC", 2);
        }
        if (lower.contains("project") || lower.contains("business")) {
            addKeyword(counts, "Project review", 2);
        }
        if (lower.contains("algorithm") || lower.contains("leetcode")) {
            addKeyword(counts, "Coding drills", 2);
        }
    }

    private void addKeyword(Map<String, Integer> counts, String keyword, int weight) {
        String normalized = keyword == null ? "" : keyword.trim();
        if (normalized.isEmpty()) {
            return;
        }
        counts.merge(normalized, weight, Integer::sum);
    }

    private String normalizeTopic(String topic) {
        return switch (topic) {
            case "Hash Table" -> "Hash table";
            case "Linked List" -> "Linked list";
            case "Design" -> "Design problems";
            case "Heap" -> "Heap";
            case "Quickselect" -> "Quickselect";
            case "Recursion" -> "Recursion";
            case "Array" -> "Array";
            case "Two Pointers" -> "Two pointers";
            default -> topic;
        };
    }

    private List<PreparationTask> buildTasks(PreparationPlanRequest request, List<String> focusAreas) {
        int days = Math.max(1, Math.min(request.days(), 14));
        List<PreparationTask> tasks = new ArrayList<>();
        List<LeetCodeProblem> problems = request.problems();
        List<InterviewExperienceSummary> interviews = request.interviews();

        for (int day = 1; day <= days; day++) {
            String theme = focusAreas.get((day - 1) % focusAreas.size());
            List<String> actions = new ArrayList<>();
            actions.add("Write three repeatable answers for high-frequency " + theme + " questions.");
            if (!interviews.isEmpty()) {
                InterviewExperienceSummary interview = interviews.get((day - 1) % interviews.size());
                actions.add("Read interview note \"" + interview.title() + "\" and extract follow-up chains.");
            }
            if (!problems.isEmpty()) {
                LeetCodeProblem problem = problems.get((day - 1) % problems.size());
                actions.add("Solve " + problem.title() + " and review complexity plus edge cases.");
            }
            actions.add("Record a five-minute STAR project walkthrough and mark weak transitions.");
            tasks.add(new PreparationTask(day, theme, "Move " + theme + " from familiar to explainable.", actions));
        }

        return tasks;
    }

    private List<RiskNote> buildRisks(PreparationPlanRequest request, List<String> focusAreas) {
        List<RiskNote> risks = new ArrayList<>();
        if (request.interviews().size() < 3) {
            risks.add(new RiskNote(
                    "high",
                    "Small evidence set",
                    "Only " + request.interviews().size() + " interview notes are included in this plan.",
                    "Search with company aliases, department names, or interview-round keywords."
            ));
        }
        if (request.problems().stream().noneMatch(problem -> "Hard".equalsIgnoreCase(problem.difficulty()))) {
            risks.add(new RiskNote(
                    "medium",
                    "No high-pressure coding drill",
                    "The current problem list has no Hard problem.",
                    "Add at least one linked-list, heap, or design Hard problem for timed practice."
            ));
        }
        if (focusAreas.stream().noneMatch(area -> area.toLowerCase(Locale.ROOT).contains("project"))) {
            risks.add(new RiskNote(
                    "medium",
                    "Project storytelling may be undertrained",
                    "Signals are weighted toward technical topics, not project deep dives.",
                    "Prepare one full project review with context, tradeoffs, metrics, and incident handling."
            ));
        }
        if (risks.isEmpty()) {
            risks.add(new RiskNote(
                    "low",
                    "Balanced rhythm",
                    "Interview notes, coding drills, and review tasks are balanced.",
                    "Follow the daily plan and remove one low-value task each evening."
            ));
        }
        return risks;
    }

    private List<String> buildChecklist(PreparationPlanRequest request, List<String> focusAreas) {
        Set<String> checklist = new LinkedHashSet<>();
        checklist.add("Prepare a 60-second self-introduction with a clear role-fit ending.");
        checklist.add("Prepare three questions for " + (request.company().isBlank() ? "the target company" : request.company()) + ".");
        focusAreas.stream().limit(4).forEach(area -> checklist.add("Finish spoken answers and follow-up prompts for " + area + "."));
        checklist.add("Create a one-page resume project risk sheet: challenge, tradeoff, result, improvement.");
        return new ArrayList<>(checklist);
    }

    private int calculateReadinessScore(PreparationPlanRequest request, List<String> focusAreas) {
        int score = 45;
        score += Math.min(request.interviews().size(), 8) * 4;
        score += Math.min(request.problems().size(), 8) * 2;
        score += Math.min(focusAreas.size(), 6) * 3;
        if (!request.company().isBlank()) {
            score += 5;
        }
        return Math.max(35, Math.min(score, 92));
    }
}
