package com.quizapp.backend.flashcard.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddFromQuizRequest(
        @NotNull(message = "quizId là bắt buộc.")
        UUID quizId,
        // Optional: null = loose cards (study now without saving).
        UUID collectionId
) {
}
