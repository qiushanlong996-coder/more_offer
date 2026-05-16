package com.moreoffer.controller;

import com.moreoffer.dto.PreparationPlanRequest;
import com.moreoffer.dto.PreparationPlanResponse;
import com.moreoffer.service.PreparationPlanService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/preparation-plans")
public class PreparationPlanController {

    private final PreparationPlanService preparationPlanService;

    public PreparationPlanController(PreparationPlanService preparationPlanService) {
        this.preparationPlanService = preparationPlanService;
    }

    @PostMapping("/generate")
    public PreparationPlanResponse generate(@Valid @RequestBody PreparationPlanRequest request) {
        return preparationPlanService.generate(request);
    }
}
