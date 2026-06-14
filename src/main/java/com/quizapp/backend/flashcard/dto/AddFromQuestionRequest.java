package com.quizapp.backend.flashcard.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddFromQuestionRequest(
        @NotNull(message = "questionId là bắt buộc.")
        UUID questionId,
        // Optional: null = loose card.
        UUID collectionId
) {
}
