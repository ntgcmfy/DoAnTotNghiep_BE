package com.quizapp.backend.document.dto;

import com.quizapp.backend.document.DocumentStatus;
import com.quizapp.backend.document.ProcessingStage;
import java.util.UUID;

public record DocumentStatusResponse(
        UUID documentId,
        String title,
        String originalFilename,
        DocumentStatus status,
        ProcessingStage stage,
        int progressPercent,
        String message,
        String failureReason
) {
}
