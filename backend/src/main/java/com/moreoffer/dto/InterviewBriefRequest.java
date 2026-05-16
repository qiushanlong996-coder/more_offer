package com.moreoffer.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record InterviewBriefRequest(
        @NotBlank String position,
        String company,
        List<String> keywords,
        List<InterviewExperienceSummary> interviews,
        List<LeetCodeProblem> problems
) {
    public InterviewBriefRequest {
        if (keywords == null) {
            keywords = List.of();
        }
        if (interviews == null) {
            interviews = List.of();
        }
        if (problems == null) {
            problems = List.of();
        }
    }
}
