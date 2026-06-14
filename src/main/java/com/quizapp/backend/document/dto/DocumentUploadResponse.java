package com.quizapp.backend.document.dto;

import com.quizapp.backend.document.DocumentStatus;
import java.util.UUID;

public record DocumentUploadResponse(
        UUID documentId,
        UUID jobId,
        DocumentStatus status
) {
}
