package com.moreoffer.dto;

public record TechRadarArticle(
        String id,
        String title,
        String sourceUrl,
        String source,
        String snippet,
        String publishedAt,
        double score
) {
}
