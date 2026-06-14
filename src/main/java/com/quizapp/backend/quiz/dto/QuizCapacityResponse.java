package com.quizapp.backend.quiz.dto;

public record QuizCapacityResponse(
        int estimatedQuestions,
        int usableChunks
) {
}
