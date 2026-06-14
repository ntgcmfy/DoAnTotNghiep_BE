package com.quizapp.backend.quiz.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record SubmitQuizRequest(
        @NotEmpty List<@Valid SubmittedAnswer> answers
) {
    public record SubmittedAnswer(
            @NotNull UUID questionId,
            // May be empty: a user is allowed to skip a question (counts as wrong).
            List<UUID> selectedOptionIds
    ) {
    }
}
