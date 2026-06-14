package com.quizapp.backend.quiz.dto;

import java.util.List;
import java.util.UUID;

public record SubmitQuizResponse(
        UUID attemptId,
        int totalQuestions,
        int correctQuestions,
        double score,
        List<UUID> wrongQuestionIds
) {
}
