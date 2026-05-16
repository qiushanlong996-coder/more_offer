package com.moreoffer.dto;

import java.util.List;

public record PreparationPlanResponse(
        String title,
        int readinessScore,
        List<String> focusAreas,
        List<PreparationTask> dailyTasks,
        List<String> checklist,
        List<RiskNote> risks
) {
    public record PreparationTask(
            int day,
            String theme,
            String goal,
            List<String> actions
    ) {
    }

    public record RiskNote(
            String level,
            String title,
            String evidence,
            String nextAction
    ) {
    }
}
