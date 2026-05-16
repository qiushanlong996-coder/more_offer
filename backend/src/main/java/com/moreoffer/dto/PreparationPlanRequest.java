package com.moreoffer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record PreparationPlanRequest(
        @NotBlank String position,
        String company,
        List<String> keywords,
        @Min(1) @Max(14) int days,
        List<InterviewExperienceSummary> interviews,
        List<LeetCodeProblem> problems
) {
    public PreparationPlanRequest {
        if (company == null) {
            company = "";
        }
        if (keywords == null) {
            keywords = List.of();
        }
        if (days == 0) {
            days = 5;
        }
        if (interviews == null) {
            interviews = List.of();
        }
        if (problems == null) {
            problems = List.of();
        }
    }
}
