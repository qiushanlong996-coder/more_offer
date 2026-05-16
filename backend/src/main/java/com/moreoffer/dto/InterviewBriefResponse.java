package com.moreoffer.dto;

import java.util.List;

public record InterviewBriefResponse(
        String title,
        List<PrioritySignal> prioritySignals,
        List<QuestionCluster> questionClusters,
        List<StoryPrompt> storyBank,
        List<String> followUpQuestions
) {
    public record PrioritySignal(
            String level,
            String title,
            String evidence,
            String action
    ) {
    }

    public record QuestionCluster(
            String topic,
            String likelyQuestion,
            String interviewerLens,
            List<String> drillSteps
    ) {
    }

    public record StoryPrompt(
            String theme,
            String prompt,
            List<String> proofPoints
    ) {
    }
}
