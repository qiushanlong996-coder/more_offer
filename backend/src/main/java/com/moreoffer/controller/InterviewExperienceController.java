package com.moreoffer.controller;

import com.moreoffer.dto.InterviewSearchRequest;
import com.moreoffer.dto.InterviewSearchResponse;
import com.moreoffer.service.InterviewExperienceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interview-experiences")
public class InterviewExperienceController {

    private final InterviewExperienceService interviewExperienceService;

    public InterviewExperienceController(InterviewExperienceService interviewExperienceService) {
        this.interviewExperienceService = interviewExperienceService;
    }

    @PostMapping("/search")
    public InterviewSearchResponse search(@Valid @RequestBody InterviewSearchRequest request) {
        return interviewExperienceService.search(request);
    }
}
