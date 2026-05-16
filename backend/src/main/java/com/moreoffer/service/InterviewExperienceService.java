package com.moreoffer.service;

import com.moreoffer.dto.InterviewSearchRequest;
import com.moreoffer.dto.InterviewSearchResponse;
import com.moreoffer.gateway.NiukeExperienceGateway;
import org.springframework.stereotype.Service;

@Service
public class InterviewExperienceService {

    private final NiukeExperienceGateway niukeExperienceGateway;

    public InterviewExperienceService(NiukeExperienceGateway niukeExperienceGateway) {
        this.niukeExperienceGateway = niukeExperienceGateway;
    }

    public InterviewSearchResponse search(InterviewSearchRequest request) {
        return niukeExperienceGateway.search(request);
    }
}
