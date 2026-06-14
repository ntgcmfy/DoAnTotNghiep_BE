package com.quizapp.backend.quiz;

import com.quizapp.backend.quiz.dto.AttemptDetailResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attempts")
public class AttemptController {

    private final AttemptService attemptService;

    public AttemptController(AttemptService attemptService) {
        this.attemptService = attemptService;
    }

    @GetMapping
    public List<AttemptDetailResponse> list(@AuthenticationPrincipal UUID userId) {
        return attemptService.listAttempts(userId);
    }

    @GetMapping("/{attemptId}")
    public AttemptDetailResponse get(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID attemptId
    ) {
        return attemptService.getAttempt(userId, attemptId);
    }
}
