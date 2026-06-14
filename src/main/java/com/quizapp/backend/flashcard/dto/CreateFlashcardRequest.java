package com.quizapp.backend.flashcard.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CreateFlashcardRequest(
        @NotBlank(message = "Mặt trước không được để trống.")
        String front,
        @NotBlank(message = "Mặt sau không được để trống.")
        String back,
        // Optional: null = loose card (not saved into a collection yet).
        UUID collectionId
) {
}
