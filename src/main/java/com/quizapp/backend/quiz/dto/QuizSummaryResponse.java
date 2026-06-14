package com.quizapp.backend.quiz.dto;

import com.quizapp.backend.quiz.ChoiceMode;
import com.quizapp.backend.quiz.Difficulty;
import com.quizapp.backend.quiz.QuizStatus;
import java.time.Instant;
import java.util.UUID;

public record QuizSummaryResponse(
        UUID id,
        UUID documentId,
        String documentTitle,
        QuizStatus status,
        boolean saved,
        Difficulty difficulty,
        ChoiceMode choiceMode,
        int numChoices,
        int questionCount,
        Instant createdAt
) {
}
