package com.moreoffer.controller;

import com.moreoffer.dto.TechRadarRequest;
import com.moreoffer.dto.TechRadarResponse;
import com.moreoffer.service.TechRadarService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tech-radar")
public class TechRadarController {

    private final TechRadarService techRadarService;

    public TechRadarController(TechRadarService techRadarService) {
        this.techRadarService = techRadarService;
    }

    @PostMapping("/research")
    public TechRadarResponse research(@Valid @RequestBody TechRadarRequest request) {
        return techRadarService.research(request);
    }
}
