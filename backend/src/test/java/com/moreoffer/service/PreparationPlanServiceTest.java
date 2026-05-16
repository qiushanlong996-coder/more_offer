package com.moreoffer.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moreoffer.dto.InterviewExperienceSummary;
import com.moreoffer.dto.LeetCodeProblem;
import com.moreoffer.dto.PreparationPlanRequest;
import com.moreoffer.dto.PreparationPlanResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreparationPlanServiceTest {

    private final PreparationPlanService service = new PreparationPlanService();

    @Test
    void generateBuildsActionablePlanFromInterviewsAndProblems() {
        PreparationPlanRequest request = new PreparationPlanRequest(
                "Java Backend Engineer",
                "ByteDance",
                List.of("Spring", "Redis"),
                3,
                List.of(new InterviewExperienceSummary(
                        "nowcoder-1",
                        "ByteDance Java first round",
                        "candidate",
                        "2026-05-01",
                        "https://www.nowcoder.com/discuss/1",
                        List.of("Java", "Redis"),
                        List.of("Redis cache breakdown and Spring transaction propagation"),
                        0.91
                )),
                List.of(new LeetCodeProblem(
                        "leetcode-146",
                        "LRU Cache",
                        "Medium",
                        List.of("Hash Table", "Linked List", "Design"),
                        "https://leetcode.com/problems/lru-cache/"
                ))
        );

        PreparationPlanResponse response = service.generate(request);

        assertThat(response.title()).contains("ByteDance", "Java Backend Engineer");
        assertThat(response.dailyTasks()).hasSize(3);
        assertThat(response.focusAreas()).contains("Redis", "Spring");
        assertThat(response.checklist()).isNotEmpty();
        assertThat(response.risks()).extracting(PreparationPlanResponse.RiskNote::level).contains("high");
        assertThat(response.readinessScore()).isBetween(35, 92);
    }
}
