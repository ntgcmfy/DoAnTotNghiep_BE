package com.quizapp.backend.user;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StudyHistoryResponse(
        List<AttemptSummary> attempts,
        List<ConceptMastery> conceptStats
) {
    public record AttemptSummary(
            UUID id,
            UUID quizId,
            int totalQuestions,
            int correctQuestions,
            double score,
            Instant submittedAt
    ) {
    }

    public record ConceptMastery(
            String concept,
            int seenCount,
            int correctCount,
            int wrongCount,
            double masteryScore,
            Instant nextReviewAt
    ) {
    }
}
