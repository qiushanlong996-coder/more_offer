package com.moreoffer.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moreoffer.dto.InterviewBriefRequest;
import com.moreoffer.dto.InterviewBriefResponse;
import com.moreoffer.dto.InterviewExperienceSummary;
import com.moreoffer.dto.LeetCodeProblem;
import java.util.List;
import org.junit.jupiter.api.Test;

class InterviewBriefServiceTest {

    private final InterviewBriefService service = new InterviewBriefService();

    @Test
    void generatesBriefFromInterviewAndProblemSignals() {
        InterviewBriefRequest request = new InterviewBriefRequest(
                "Java Backend Engineer",
                "ByteDance",
                List.of("Spring", "Redis"),
                List.of(new InterviewExperienceSummary(
                        "1",
                        "ByteDance backend first round",
                        "candidate",
                        "2026-05-16",
                        "https://example.com",
                        List.of("Spring", "Redis"),
                        List.of("Asked about cache breakdown"),
                        0.93
                )),
                List.of(new LeetCodeProblem(
                        "leetcode-146",
                        "LRU Cache",
                        "Medium",
                        List.of("Hash Table", "Design"),
                        "https://leetcode.com/problems/lru-cache/"
                ))
        );

        InterviewBriefResponse response = service.generate(request);

        assertThat(response.title()).contains("ByteDance", "Java Backend Engineer");
        assertThat(response.prioritySignals()).hasSize(3);
        assertThat(response.questionClusters()).isNotEmpty();
        assertThat(response.storyBank()).hasSize(3);
        assertThat(response.followUpQuestions()).hasSize(4);
    }
}
