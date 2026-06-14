package com.quizapp.backend.user;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
public class StudyHistoryController {
    private final StudyHistoryService studyHistoryService;

    public StudyHistoryController(StudyHistoryService studyHistoryService) {
        this.studyHistoryService = studyHistoryService;
    }

    @GetMapping("/history")
    public StudyHistoryResponse history(@AuthenticationPrincipal UUID userId) {
        return studyHistoryService.getHistory(userId);
    }
}
