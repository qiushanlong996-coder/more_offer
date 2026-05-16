package com.moreoffer.dto;

import java.util.List;

public record InterviewExperienceSummary(
        String id,
        String title,
        String author,
        String publishedAt,
        String sourceUrl,
        List<String> tags,
        List<String> highlights,
        double score
) {
}
