package com.moreoffer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

public record TechRadarRequest(
        String topic,
        String position,
        String company,
        List<String> keywords,
        @Min(1) @Max(20) int limit
) {
    public TechRadarRequest {
        if (keywords == null) {
            keywords = List.of();
        }
        if (limit == 0) {
            limit = 8;
        }
    }
}
