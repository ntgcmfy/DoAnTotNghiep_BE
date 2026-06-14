package com.quizapp.backend.quiz.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record QuizCapacityRequest(
        @NotNull UUID documentId,
        List<UUID> chunkIds
) {
}
