package com.quizapp.backend.flashcard.dto;

import java.time.Instant;
import java.util.UUID;

public record CollectionResponse(
        UUID id,
        String name,
        int cardCount,
        Instant createdAt
) {
}
