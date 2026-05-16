package com.moreoffer.gateway;

import java.util.List;

public interface WebRooterMcpGateway {

    WebRooterSearchResult searchTech(String query, int limit);

    record WebRooterSearchResult(
            String query,
            List<WebRooterArticleCandidate> articles,
            String referencesText
    ) {
    }

    record WebRooterArticleCandidate(
            String title,
            String url,
            String snippet,
            String engine,
            String publishedAt,
            int rank
    ) {
    }
}
