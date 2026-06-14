package com.quizapp.backend.document.dto;

import com.quizapp.backend.document.DocumentStatus;
import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String originalFilename,
        String title,
        String languageCode,
        DocumentStatus status,
        Instant createdAt
) {
}
