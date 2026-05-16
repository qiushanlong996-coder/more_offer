package com.moreoffer.dto;

import java.util.List;

public record LeetCodeProblemResponse(
        String position,
        List<LeetCodeProblem> items
) {
}
