package com.moreoffer.controller;

import com.moreoffer.dto.LeetCodeProblemResponse;
import com.moreoffer.service.LeetCodeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leetcode")
public class LeetCodeController {

    private final LeetCodeService leetCodeService;

    public LeetCodeController(LeetCodeService leetCodeService) {
        this.leetCodeService = leetCodeService;
    }

    @GetMapping("/hot")
    public LeetCodeProblemResponse hot(
            @RequestParam(defaultValue = "java-backend") String position,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return leetCodeService.hot(position, limit);
    }
}
