package com.moreoffer.dto;

import java.util.List;

public record TechRadarResponse(
        String query,
        String summary,
        boolean generatedByOpenAi,
        List<String> themes,
        List<TechRadarArticle> articles,
        List<String> interviewSignals,
        String source,
        String generatedAt
) {
}
