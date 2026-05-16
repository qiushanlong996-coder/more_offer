package com.moreoffer.dto;

import java.util.List;

public record LeetCodeProblem(
        String id,
        String title,
        String difficulty,
        List<String> topics,
        String url
) {
}
