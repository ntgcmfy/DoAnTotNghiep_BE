package com.quizapp.backend.quiz.dto;

import java.util.UUID;

public record AnswerOptionResponse(
        UUID id,
        int position,
        String text
) {
}
