package com.moreoffer.service;

import com.moreoffer.dto.LeetCodeProblem;
import com.moreoffer.dto.LeetCodeProblemResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class LeetCodeService {

    private static final List<LeetCodeProblem> JAVA_BACKEND_HOT = List.of(
            new LeetCodeProblem("leetcode-146", "LRU Cache", "Medium", List.of("Hash Table", "Linked List", "Design"), "https://leetcode.com/problems/lru-cache/"),
            new LeetCodeProblem("leetcode-215", "Kth Largest Element in an Array", "Medium", List.of("Heap", "Quickselect"), "https://leetcode.com/problems/kth-largest-element-in-an-array/"),
            new LeetCodeProblem("leetcode-206", "Reverse Linked List", "Easy", List.of("Linked List", "Recursion"), "https://leetcode.com/problems/reverse-linked-list/"),
            new LeetCodeProblem("leetcode-25", "Reverse Nodes in k-Group", "Hard", List.of("Linked List", "Recursion"), "https://leetcode.com/problems/reverse-nodes-in-k-group/"),
            new LeetCodeProblem("leetcode-15", "3Sum", "Medium", List.of("Array", "Two Pointers"), "https://leetcode.com/problems/3sum/")
    );

    public LeetCodeProblemResponse hot(String position, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, JAVA_BACKEND_HOT.size()));
        return new LeetCodeProblemResponse(position, JAVA_BACKEND_HOT.subList(0, safeLimit));
    }
}
