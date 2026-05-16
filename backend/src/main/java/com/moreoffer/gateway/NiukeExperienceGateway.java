package com.moreoffer.gateway;

import com.moreoffer.dto.InterviewSearchRequest;
import com.moreoffer.dto.InterviewSearchResponse;

public interface NiukeExperienceGateway {

    InterviewSearchResponse search(InterviewSearchRequest request);
}
