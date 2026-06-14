package com.quizapp.backend.flashcard.dto;

import java.util.UUID;

public record MoveFlashcardRequest(
        // null = move back to loose (uncollected).
        UUID collectionId
) {
}
