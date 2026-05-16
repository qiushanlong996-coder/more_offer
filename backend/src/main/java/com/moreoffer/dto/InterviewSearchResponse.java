package com.moreoffer.dto;

import java.util.List;

public record InterviewSearchResponse(
        String query,
        int total,
        List<InterviewExperienceSummary> items
) {
}
