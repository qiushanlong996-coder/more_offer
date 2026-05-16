package com.moreoffer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record InterviewSearchRequest(
        @NotBlank String position,
        String company,
        List<String> keywords,
        @Min(1) int page,
        @Min(1) @Max(30) int size
) {
    public InterviewSearchRequest {
        if (page == 0) {
            page = 1;
        }
        if (size == 0) {
            size = 10;
        }
        if (keywords == null) {
            keywords = List.of();
        }
    }
}
