package com.moreoffer.controller;

import com.moreoffer.dto.InterviewBriefRequest;
import com.moreoffer.dto.InterviewBriefResponse;
import com.moreoffer.service.InterviewBriefService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interview-briefs")
public class InterviewBriefController {

    private final InterviewBriefService interviewBriefService;

    public InterviewBriefController(InterviewBriefService interviewBriefService) {
        this.interviewBriefService = interviewBriefService;
    }

    @PostMapping("/generate")
    public InterviewBriefResponse generate(@Valid @RequestBody InterviewBriefRequest request) {
        return interviewBriefService.generate(request);
    }
}
