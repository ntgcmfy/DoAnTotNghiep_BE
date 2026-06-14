package com.quizapp.backend.quiz.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateQuizRequest(
        @NotNull(message = "Trường 'saved' là bắt buộc.")
        Boolean saved
) {
}
