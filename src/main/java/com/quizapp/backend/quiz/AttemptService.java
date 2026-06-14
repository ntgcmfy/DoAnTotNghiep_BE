package com.quizapp.backend.quiz;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizapp.backend.common.ApiException;
import com.quizapp.backend.document.DocumentChunkEntity;
import com.quizapp.backend.document.repo.DocumentChunkRepository;
import com.quizapp.backend.quiz.dto.AttemptDetailResponse;
import com.quizapp.backend.quiz.dto.AttemptDetailResponse.AllOption;
import com.quizapp.backend.quiz.dto.AttemptDetailResponse.AttemptAnswerDetail;
import com.quizapp.backend.quiz.dto.AttemptDetailResponse.CorrectOption;
import com.quizapp.backend.quiz.dto.AttemptDetailResponse.SourceContext;
import com.quizapp.backend.quiz.repo.AnswerOptionRepository;
import com.quizapp.backend.quiz.repo.AttemptAnswerRepository;
import com.quizapp.backend.quiz.repo.QuizAttemptRepository;
import com.quizapp.backend.quiz.repo.QuizQuestionRepository;
import com.quizapp.backend.quiz.repo.QuizRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttemptService {

    private final QuizAttemptRepository quizAttemptRepository;
    private final AttemptAnswerRepository attemptAnswerRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final AnswerOptionRepository answerOptionRepository;
    private final QuizRepository quizRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final ObjectMapper objectMapper;

    public AttemptService(
            QuizAttemptRepository quizAttemptRepository,
            AttemptAnswerRepository attemptAnswerRepository,
            QuizQuestionRepository quizQuestionRepository,
            AnswerOptionRepository answerOptionRepository,
            QuizRepository quizRepository,
            DocumentChunkRepository documentChunkRepository,
            ObjectMapper objectMapper
    ) {
        this.quizAttemptRepository = quizAttemptRepository;
        this.attemptAnswerRepository = attemptAnswerRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.answerOptionRepository = answerOptionRepository;
        this.quizRepository = quizRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * List the last 20 attempts for the current user.
     */
    @Transactional(readOnly = true)
    public List<AttemptDetailResponse> listAttempts(UUID ownerId) {
        return quizAttemptRepository.findTop20ByOwnerIdOrderBySubmittedAtDesc(ownerId).stream()
                .map(attempt -> buildDetail(attempt, false))
                .toList();
    }

    /**
     * Get a single attempt with full per-question breakdown.
     */
    @Transactional(readOnly = true)
    public AttemptDetailResponse getAttempt(UUID ownerId, UUID attemptId) {
        QuizAttemptEntity attempt = quizAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Attempt not found."));
        if (!attempt.getOwnerId().equals(ownerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Access denied.");
        }
        return buildDetail(attempt, true);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private AttemptDetailResponse buildDetail(QuizAttemptEntity attempt, boolean includeAnswers) {
        List<AttemptAnswerDetail> answerDetails = new ArrayList<>();

        QuizEntity quiz = quizRepository.findById(attempt.getQuizId()).orElse(null);
        UUID documentId = quiz != null ? quiz.getDocumentId() : null;

        if (includeAnswers) {
            List<AttemptAnswerEntity> attemptAnswers = attemptAnswerRepository.findByAttemptId(attempt.getId());

            List<UUID> questionIds = attemptAnswers.stream()
                    .map(AttemptAnswerEntity::getQuestionId)
                    .toList();

            Map<UUID, QuizQuestionEntity> questionById = quizQuestionRepository.findAllById(questionIds).stream()
                    .collect(Collectors.toMap(QuizQuestionEntity::getId, q -> q));

            Map<UUID, List<AnswerOptionEntity>> optionsByQuestion =
                    answerOptionRepository.findByQuestionIdInOrderByQuestionIdAscPositionAsc(questionIds)
                            .stream()
                            .collect(Collectors.groupingBy(AnswerOptionEntity::getQuestionId));

            // Lấy CONTEXT — gom tất cả chunkId của các câu hỏi, query 1 lần.
            List<UUID> chunkIds = questionById.values().stream()
                    .map(QuizQuestionEntity::getChunkId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();
            Map<UUID, DocumentChunkEntity> chunkById = documentChunkRepository.findAllById(chunkIds).stream()
                    .collect(Collectors.toMap(DocumentChunkEntity::getId, c -> c));

            for (AttemptAnswerEntity aa : attemptAnswers) {
                QuizQuestionEntity question = questionById.get(aa.getQuestionId());
                if (question == null) continue;

                List<AnswerOptionEntity> opts = optionsByQuestion
                        .getOrDefault(aa.getQuestionId(), List.of()).stream()
                        .sorted(Comparator.comparing(AnswerOptionEntity::getPosition))
                        .toList();

                List<UUID> selectedIds = readUuidList(aa.getSelectedOptionIdsJson());
                List<CorrectOption> correctOptions = opts.stream()
                        .filter(AnswerOptionEntity::isCorrect)
                        .map(o -> new CorrectOption(o.getId(), o.getAnswerText()))
                        .toList();
                List<AllOption> allOptions = opts.stream()
                        .map(o -> new AllOption(o.getId(), o.getAnswerText()))
                        .toList();

                DocumentChunkEntity chunk = chunkById.get(question.getChunkId());
                SourceContext src = chunk == null ? null : new SourceContext(
                        chunk.getId(), chunk.getPageStart(), chunk.getPageEnd(), chunk.getText());

                answerDetails.add(new AttemptAnswerDetail(
                        aa.getQuestionId(),
                        question.getQuestionText(),
                        aa.isCorrect(),
                        selectedIds,
                        correctOptions,
                        allOptions,
                        src));
            }
        }

        return new AttemptDetailResponse(
                attempt.getId(),
                attempt.getQuizId(),
                documentId,
                attempt.getTotalQuestions(),
                attempt.getCorrectQuestions(),
                attempt.getScore(),
                attempt.getSubmittedAt(),
                answerDetails);
    }

    private List<UUID> readUuidList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (IOException e) {
            return List.of();
        }
    }
}
