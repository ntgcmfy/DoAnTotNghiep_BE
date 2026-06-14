package com.quizapp.backend.flashcard.dto;

import java.time.Instant;
import java.util.UUID;

public record FlashcardResponse(
        UUID id,
        UUID collectionId,
        String front,
        String back,
        UUID sourceQuestionId,
        UUID sourceDocumentId,
        Instant createdAt
) {
}
