package com.quizapp.backend.quiz.dto;

import com.quizapp.backend.quiz.QuizStatus;
import java.util.List;
import java.util.UUID;

public record QuizResponse(
        UUID id,
        UUID documentId,
        QuizStatus status,
        List<QuizQuestionResponse> questions
) {
}
