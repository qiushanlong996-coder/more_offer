package com.moreoffer.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moreoffer.config.OpenAiProperties;
import com.moreoffer.dto.TechRadarRequest;
import com.moreoffer.dto.TechRadarResponse;
import com.moreoffer.gateway.WebRooterMcpGateway;
import java.util.List;
import org.junit.jupiter.api.Test;

class TechRadarServiceTest {

    private final WebRooterMcpGateway gateway = (query, limit) -> new WebRooterMcpGateway.WebRooterSearchResult(
            query,
            List.of(
                    new WebRooterMcpGateway.WebRooterArticleCandidate(
                            "Redis caching patterns for high traffic Java services",
                            "https://example.com/redis-caching",
                            "Production cache invalidation, observability, and performance trade-offs.",
                            "github",
                            1
                    ),
                    new WebRooterMcpGateway.WebRooterArticleCandidate(
                            "Spring Boot incident review",
                            "https://example.com/spring-incident",
                            "Failure handling and retry boundaries for backend systems.",
                            "medium",
                            2
                    )
            ),
            ""
    );

    private final TechRadarService service = new TechRadarService(
            gateway,
            new OpenAiSummaryService(new OpenAiProperties(), new ObjectMapper())
    );

    @Test
    void researchBuildsRadarFromWebRooterResults() {
        TechRadarResponse response = service.research(new TechRadarRequest(
                "",
                "Java Backend Engineer",
                "ByteDance",
                List.of("Spring", "Redis"),
                5
        ));

        assertThat(response.query()).contains("Java backend", "backend architecture");
        assertThat(response.generatedByOpenAi()).isFalse();
        assertThat(response.articles()).hasSize(2);
        assertThat(response.themes()).contains("Spring", "Redis");
        assertThat(response.interviewSignals()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(response.summary()).contains("Web-Rooter");
    }
}
