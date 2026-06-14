package com.quizapp.backend.quiz.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AttemptDetailResponse(
        UUID attemptId,
        UUID quizId,
        UUID documentId,
        int totalQuestions,
        int correctQuestions,
        double score,
        Instant submittedAt,
        List<AttemptAnswerDetail> answers
) {
    public record AttemptAnswerDetail(
            UUID questionId,
            String questionText,
            boolean correct,
            List<UUID> selectedOptionIds,
            List<CorrectOption> correctOptions,
            List<AllOption> allOptions,
            // Ngữ cảnh đoạn văn câu hỏi được sinh ra (để user xem khi review)
            SourceContext sourceContext
    ) {}

    public record SourceContext(
            UUID chunkId,
            int pageStart,
            int pageEnd,
            String text
    ) {}

    public record CorrectOption(UUID id, String answerText) {}

    public record AllOption(UUID id, String answerText) {}
}
