package com.quizapp.backend.quiz;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizapp.backend.common.ApiException;
import com.quizapp.backend.document.DocumentChunkEntity;
import com.quizapp.backend.document.DocumentEntity;
import com.quizapp.backend.document.DocumentService;
import com.quizapp.backend.document.DocumentStatus;
import com.quizapp.backend.document.repo.DocumentChunkRepository;
import com.quizapp.backend.document.repo.DocumentRepository;
import com.quizapp.backend.quiz.QuestionGeneratorClient.GeneratedQuestion;
import com.quizapp.backend.quiz.QuestionGeneratorClient.GeneratorChunk;
import com.quizapp.backend.quiz.dto.AnswerOptionResponse;
import com.quizapp.backend.quiz.dto.GenerateQuizRequest;
import com.quizapp.backend.quiz.dto.PersonalizationOptions;
import com.quizapp.backend.quiz.dto.QuestionBatchSpec;
import com.quizapp.backend.quiz.dto.QuizCapacityResponse;
import com.quizapp.backend.quiz.dto.QuizQuestionResponse;
import com.quizapp.backend.quiz.dto.QuizResponse;
import com.quizapp.backend.quiz.dto.QuizSummaryResponse;
import com.quizapp.backend.quiz.dto.SubmitQuizRequest;
import com.quizapp.backend.quiz.dto.SubmitQuizResponse;
import com.quizapp.backend.quiz.repo.AnswerOptionRepository;
import com.quizapp.backend.quiz.repo.AttemptAnswerRepository;
import com.quizapp.backend.quiz.repo.QuizAttemptRepository;
import com.quizapp.backend.quiz.repo.QuizQuestionRepository;
import com.quizapp.backend.quiz.repo.QuizRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizService {
    private static final Logger log = LoggerFactory.getLogger(QuizService.class);

    private final DocumentService documentService;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final QuestionGeneratorClient questionGeneratorClient;
    private final PersonalizationService personalizationService;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final AnswerOptionRepository answerOptionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final AttemptAnswerRepository attemptAnswerRepository;
    private final ObjectMapper objectMapper;

    public QuizService(
            DocumentService documentService,
            DocumentRepository documentRepository,
            DocumentChunkRepository documentChunkRepository,
            QuestionGeneratorClient questionGeneratorClient,
            PersonalizationService personalizationService,
            QuizRepository quizRepository,
            QuizQuestionRepository quizQuestionRepository,
            AnswerOptionRepository answerOptionRepository,
            QuizAttemptRepository quizAttemptRepository,
            AttemptAnswerRepository attemptAnswerRepository,
            ObjectMapper objectMapper
    ) {
        this.documentService = documentService;
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.questionGeneratorClient = questionGeneratorClient;
        this.personalizationService = personalizationService;
        this.quizRepository = quizRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.answerOptionRepository = answerOptionRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.attemptAnswerRepository = attemptAnswerRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public QuizResponse generate(UUID ownerId, GenerateQuizRequest request) {
        DocumentEntity document = documentService.findOwnedDocument(ownerId, request.documentId());
        if (document.getStatus() != DocumentStatus.READY) {
            throw new ApiException(HttpStatus.CONFLICT, "Document is not ready for quiz generation.");
        }

        validatePlan(request.quizPlan());

        QuizEntity quiz = new QuizEntity();
        quiz.setOwnerId(ownerId);
        quiz.setDocumentId(document.getId());
        quiz.setStatus(QuizStatus.GENERATED);
        quiz.setPlanJson(writeJson(request.quizPlan()));
        quizRepository.save(quiz);

        int position = 1;
        for (QuestionBatchSpec spec : request.quizPlan()) {
            List<DocumentChunkEntity> chunks = selectChunks(ownerId, document.getId(), spec, options(request));
            List<GeneratorChunk> generatorChunks = chunks.stream()
                    .limit(Math.max(3, spec.quantity()))
                    .map(chunk -> new GeneratorChunk(chunk.getId(), chunk.getText(), readStringList(chunk.getConceptsJson())))
                    .toList();

            List<GeneratedQuestion> generatedQuestions = questionGeneratorClient.generate(
                    generatorChunks,
                    spec.quantity(),
                    spec.difficulty(),
                    spec.numChoices(),
                    spec.numCorrect());

            Map<UUID, DocumentChunkEntity> chunkById = chunks.stream()
                    .collect(Collectors.toMap(DocumentChunkEntity::getId, Function.identity(), (a, b) -> a));

            for (GeneratedQuestion generated : generatedQuestions.stream().limit(spec.quantity()).toList()) {
                DocumentChunkEntity sourceChunk = chunkById.getOrDefault(
                        generated.sourceChunkId(),
                        chunks.get((position - 1) % chunks.size()));
                position = saveGeneratedQuestion(quiz, spec, generated, sourceChunk, position);
            }
        }

        if (position == 1) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Question generator did not produce usable questions.");
        }
        return getQuiz(ownerId, quiz.getId(), false);
    }

    @Transactional(readOnly = true)
    public QuizCapacityResponse estimateCapacity(UUID ownerId, UUID documentId, List<UUID> chunkIds) {
        documentService.findOwnedDocument(ownerId, documentId); // ownership + existence check
        List<DocumentChunkEntity> chunks = documentChunkRepository
                .findByDocumentIdAndQuizabilityScoreGreaterThanEqualOrderByChunkIndex(documentId, 0.45);
        if (chunkIds != null && !chunkIds.isEmpty()) {
            Set<UUID> allowed = new HashSet<>(chunkIds);
            chunks = chunks.stream().filter(chunk -> allowed.contains(chunk.getId())).toList();
        }
        if (chunks.isEmpty()) {
            return new QuizCapacityResponse(0, 0);
        }
        List<GeneratorChunk> generatorChunks = chunks.stream()
                .map(chunk -> new GeneratorChunk(chunk.getId(), chunk.getText(), readStringList(chunk.getConceptsJson())))
                .toList();
        try {
            QuestionGeneratorClient.CapacityResult result = questionGeneratorClient.estimateCapacity(generatorChunks);
            return new QuizCapacityResponse(result.estimatedQuestions(), result.usableChunks());
        } catch (Exception e) {
            log.debug("AI capacity estimate unavailable, using weighted local heuristic: {}", e.getMessage());
            // Dùng quizabilityScore để ước tính thay vì flat × 3 (tài liệu nhiều code sẽ có score thấp hơn).
            int estimated = (int) Math.round(chunks.stream()
                    .mapToDouble(c -> c.getQuizabilityScore() * 3.0)
                    .sum());
            return new QuizCapacityResponse(Math.max(estimated, 0), chunks.size());
        }
    }

    @Transactional(readOnly = true)
    public QuizResponse getQuiz(UUID ownerId, UUID quizId, boolean includeCorrectAnswers) {
        QuizEntity quiz = quizRepository.findByIdAndOwnerId(quizId, ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Quiz not found."));
        List<QuizQuestionEntity> questions = quizQuestionRepository.findByQuizIdOrderByPosition(quizId);
        List<UUID> questionIds = questions.stream().map(QuizQuestionEntity::getId).toList();
        Map<UUID, List<AnswerOptionEntity>> optionsByQuestionId = answerOptionRepository
                .findByQuestionIdInOrderByQuestionIdAscPositionAsc(questionIds)
                .stream()
                .collect(Collectors.groupingBy(
                        AnswerOptionEntity::getQuestionId,
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<QuizQuestionResponse> questionResponses = questions.stream()
                .map(question -> toQuestionResponse(question, optionsByQuestionId.getOrDefault(question.getId(), List.of())))
                .toList();
        return new QuizResponse(quiz.getId(), quiz.getDocumentId(), quiz.getStatus(), questionResponses);
    }

    @Transactional
    public SubmitQuizResponse submit(UUID ownerId, UUID quizId, SubmitQuizRequest request) {
        QuizEntity quiz = quizRepository.findByIdAndOwnerId(quizId, ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Quiz not found."));
        List<QuizQuestionEntity> questions = quizQuestionRepository.findByQuizIdOrderByPosition(quizId);
        Map<UUID, QuizQuestionEntity> questionById = questions.stream()
                .collect(Collectors.toMap(QuizQuestionEntity::getId, Function.identity()));

        Map<UUID, List<AnswerOptionEntity>> optionsByQuestionId = new HashMap<>();
        for (QuizQuestionEntity question : questions) {
            optionsByQuestionId.put(question.getId(), answerOptionRepository.findByQuestionIdOrderByPosition(question.getId()));
        }

        QuizAttemptEntity attempt = new QuizAttemptEntity();
        attempt.setQuizId(quizId);
        attempt.setOwnerId(ownerId);
        attempt.setTotalQuestions(questions.size());
        quizAttemptRepository.save(attempt);

        int correctCount = 0;
        List<UUID> wrongQuestionIds = new ArrayList<>();
        for (SubmitQuizRequest.SubmittedAnswer answer : request.answers()) {
            QuizQuestionEntity question = questionById.get(answer.questionId());
            if (question == null) {
                continue;
            }

            Set<UUID> correctOptionIds = optionsByQuestionId.getOrDefault(question.getId(), List.of()).stream()
                    .filter(AnswerOptionEntity::isCorrect)
                    .map(AnswerOptionEntity::getId)
                    .collect(Collectors.toSet());
            List<UUID> selectedOptionIds = answer.selectedOptionIds() == null
                    ? List.of()
                    : answer.selectedOptionIds();
            Set<UUID> selectedIds = new HashSet<>(selectedOptionIds);
            boolean correct = !selectedIds.isEmpty() && selectedIds.equals(correctOptionIds);
            if (correct) {
                correctCount++;
            } else {
                wrongQuestionIds.add(question.getId());
            }

            AttemptAnswerEntity attemptAnswer = new AttemptAnswerEntity();
            attemptAnswer.setAttemptId(attempt.getId());
            attemptAnswer.setQuestionId(question.getId());
            attemptAnswer.setSelectedOptionIdsJson(writeJson(selectedOptionIds));
            attemptAnswer.setCorrect(correct);
            attemptAnswerRepository.save(attemptAnswer);

            personalizationService.recordQuestionResult(
                    ownerId,
                    quiz.getDocumentId(),
                    readStringList(question.getConceptsJson()),
                    correct);
        }

        attempt.setCorrectQuestions(correctCount);
        attempt.setScore(questions.isEmpty() ? 0 : (double) correctCount / questions.size());
        quizAttemptRepository.save(attempt);

        quiz.setStatus(QuizStatus.SUBMITTED);
        quizRepository.save(quiz);
        return new SubmitQuizResponse(attempt.getId(), questions.size(), correctCount, attempt.getScore(), wrongQuestionIds);
    }

    @Transactional(readOnly = true)
    public List<QuizSummaryResponse> listQuizzes(UUID ownerId) {
        return quizRepository.findTop20ByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(quiz -> toSummary(quiz, documentRepository.findById(quiz.getDocumentId())
                        .map(DocumentEntity::getTitle)
                        .orElse(null)))
                .toList();
    }

    @Transactional
    public QuizSummaryResponse setSaved(UUID ownerId, UUID quizId, boolean saved) {
        QuizEntity quiz = quizRepository.findByIdAndOwnerId(quizId, ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Quiz not found."));
        quiz.setSaved(saved);
        quizRepository.save(quiz);
        return toSummary(quiz, documentRepository.findById(quiz.getDocumentId())
                .map(DocumentEntity::getTitle)
                .orElse(null));
    }

    private QuizSummaryResponse toSummary(QuizEntity quiz, String docTitle) {
        QuestionBatchSpec firstSpec = firstSpec(quiz.getPlanJson());
        return new QuizSummaryResponse(
                quiz.getId(),
                quiz.getDocumentId(),
                docTitle,
                quiz.getStatus(),
                quiz.isSaved(),
                firstSpec == null ? null : firstSpec.difficulty(),
                firstSpec == null ? null : firstSpec.choiceMode(),
                firstSpec == null ? 0 : firstSpec.numChoices(),
                quizQuestionRepository.countByQuizId(quiz.getId()),
                quiz.getCreatedAt());
    }

    private QuestionBatchSpec firstSpec(String planJson) {
        if (planJson == null || planJson.isBlank()) {
            return null;
        }
        try {
            List<QuestionBatchSpec> plan = objectMapper.readValue(planJson, new TypeReference<>() {});
            return plan == null || plan.isEmpty() ? null : plan.get(0);
        } catch (IOException exception) {
            return null;
        }
    }

    @Transactional
    public QuizResponse generateAdaptive(UUID ownerId, UUID quizId) {
        QuizEntity source = quizRepository.findByIdAndOwnerId(quizId, ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Quiz not found."));
        UUID documentId = source.getDocumentId();

        // Giữ cấu hình (độ khó, số đáp án...) từ quiz gốc.
        QuestionBatchSpec spec = firstSpec(source.getPlanJson());
        if (spec == null) {
            spec = new QuestionBatchSpec(10, Difficulty.MEDIUM, QuestionType.MULTIPLE_CHOICE,
                    ChoiceMode.SINGLE_CORRECT, 4, 1, null);
        }
        int target = spec.quantity();

        // 1) Lấy mastery hiện tại của user với tài liệu này.
        //    weakConcepts → dùng để lọc câu sai (bỏ qua câu đã thành thạo rồi).
        //    masteryByName → dùng để sắp xếp câu sai (yếu nhất lên trước).
        Set<String> weakConceptNames = personalizationService.weakConcepts(ownerId, documentId);
        Map<String, Double> masteryByName = personalizationService.masteryMap(ownerId, documentId);

        // 2) Gom tất cả quiz trên tài liệu này, mới nhất trước.
        List<QuizEntity> docQuizzes = quizRepository.findByDocumentIdAndOwnerId(documentId, ownerId)
                .stream()
                .sorted(Comparator.comparing(QuizEntity::getCreatedAt).reversed())
                .toList();

        // 3) Tập câu đã dùng (tránh trùng + ước lượng document đã cạn câu chưa).
        Set<String> usedTexts = new HashSet<>();
        for (QuizEntity q : docQuizzes) {
            for (QuizQuestionEntity qq : quizQuestionRepository.findByQuizIdOrderByPosition(q.getId())) {
                usedTexts.add(normalizeQuestionText(qq.getQuestionText()));
            }
        }

        // 4) Gom câu SAI từ TẤT CẢ quiz trên tài liệu (mới nhất trước → putIfAbsent giữ phiên bản mới nhất).
        //    Chỉ giữ câu có ít nhất 1 concept CÒN YẾU — câu đã thành thạo thì không cần ôn lại.
        Map<String, QuizQuestionEntity> wrongByText = new LinkedHashMap<>();
        for (QuizEntity q : docQuizzes) {
            quizAttemptRepository.findFirstByQuizIdAndOwnerIdOrderBySubmittedAtDesc(q.getId(), ownerId)
                    .ifPresent(attempt -> {
                        List<UUID> wrongIds = attemptAnswerRepository.findByAttemptId(attempt.getId()).stream()
                                .filter(a -> !a.isCorrect())
                                .map(AttemptAnswerEntity::getQuestionId)
                                .toList();
                        quizQuestionRepository.findAllById(wrongIds).forEach(qq -> {
                            List<String> concepts = readStringList(qq.getConceptsJson());
                            // Không có lịch sử → chưa biết gì → đưa tất cả vào.
                            // Có concept → giữ nếu ít nhất 1 concept còn yếu.
                            boolean stillWeak = weakConceptNames.isEmpty()
                                    || concepts.isEmpty()
                                    || concepts.stream().anyMatch(weakConceptNames::contains);
                            if (stillWeak) {
                                wrongByText.putIfAbsent(normalizeQuestionText(qq.getQuestionText()), qq);
                            }
                        });
                    });
        }

        // 5) Sắp xếp câu sai: concept yếu nhất lên trước (mastery thấp = cần ôn nhất).
        List<QuizQuestionEntity> wrongQuestions = wrongByText.values().stream()
                .sorted(Comparator.comparingDouble(qq -> avgMastery(qq, masteryByName)))
                .toList();

        // 6) Ước tính tài liệu đã cạn câu chưa (≈ usableChunks × 3).
        int usableChunks = documentChunkRepository
                .findByDocumentIdAndQuizabilityScoreGreaterThanEqualOrderByChunkIndex(documentId, 0.45).size();
        int maxQuestions = Math.max(1, usableChunks * 3);
        boolean documentMaxedOut = usedTexts.size() >= maxQuestions;

        // 7) Tạo quiz mới.
        QuizEntity newQuiz = new QuizEntity();
        newQuiz.setOwnerId(ownerId);
        newQuiz.setDocumentId(documentId);
        newQuiz.setStatus(QuizStatus.GENERATED);
        newQuiz.setPlanJson(writeJson(List.of(spec)));
        quizRepository.save(newQuiz);

        // 8) Copy câu sai vào quiz mới, XÁO TRỘN đáp án trong từng câu (tránh nhớ vị trí).
        //    Position được gán ngay từ đây (tăng dần), tránh vi phạm unique(quiz_id, position)
        //    khi Hibernate flush trước khi bước 10 kịp gán lại.
        List<QuizQuestionEntity> newQuestions = new ArrayList<>();
        int pos = 1;
        for (QuizQuestionEntity wrong : wrongQuestions) {
            newQuestions.add(copyQuestionWithShuffledOptions(newQuiz, wrong, pos++));
        }

        // 9) Sinh thêm câu MỚI nếu cần.
        //    Chunk được ưu tiên theo concept yếu (PersonalizationOptions.defaults() bật personalization).
        int newNeeded = target - newQuestions.size();
        if (!documentMaxedOut && newNeeded > 0) {
            List<DocumentChunkEntity> chunks = selectChunks(ownerId, documentId, spec, PersonalizationOptions.defaults());
            if (!chunks.isEmpty()) {
                List<GeneratorChunk> genChunks = chunks.stream()
                        .map(c -> new GeneratorChunk(c.getId(), c.getText(), readStringList(c.getConceptsJson())))
                        .toList();
                try {
                    // Sinh dư (+5) để bù số câu bị loại vì trùng.
                    List<GeneratedQuestion> generated = questionGeneratorClient.generate(
                            genChunks, newNeeded + 5, spec.difficulty(), spec.numChoices(), spec.numCorrect());
                    Map<UUID, DocumentChunkEntity> chunkById = chunks.stream()
                            .collect(Collectors.toMap(DocumentChunkEntity::getId, Function.identity(), (a, b) -> a));
                    for (GeneratedQuestion g : generated) {
                        if (newNeeded <= 0) break;
                        if (g.question() == null || g.question().isBlank()) continue;
                        String key = normalizeQuestionText(g.question());
                        if (usedTexts.contains(key)) continue;
                        DocumentChunkEntity sc = chunkById.getOrDefault(g.sourceChunkId(), chunks.get(0));
                        QuizQuestionEntity saved = saveGeneratedQuestionEntity(newQuiz, spec, g, sc, pos);
                        if (saved != null) {
                            usedTexts.add(key);
                            newQuestions.add(saved);
                            newNeeded--;
                            pos++;
                        }
                    }
                } catch (Exception e) {
                    // AI service không khả dụng. Nếu đã có câu sai thì vẫn tạo quiz với các câu đó.
                    log.warn("AI service unavailable during adaptive quiz generation: {}", e.getMessage());
                }
            }
        }

        if (newQuestions.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Không tạo được câu hỏi ôn tập. Vui lòng thử lại sau.");
        }

        return getQuiz(ownerId, newQuiz.getId(), false);
    }

    /** Sao chép 1 câu hỏi sang quiz mới, xáo trộn thứ tự đáp án. Position được truyền vào để đảm bảo unique. */
    private QuizQuestionEntity copyQuestionWithShuffledOptions(QuizEntity newQuiz, QuizQuestionEntity src, int position) {
        QuizQuestionEntity copy = new QuizQuestionEntity();
        copy.setQuizId(newQuiz.getId());
        copy.setChunkId(src.getChunkId());
        copy.setPosition(position);
        copy.setDifficulty(src.getDifficulty());
        copy.setChoiceMode(src.getChoiceMode());
        copy.setNumCorrect(src.getNumCorrect());
        copy.setQuestionText(src.getQuestionText());
        copy.setConceptsJson(src.getConceptsJson());
        quizQuestionRepository.save(copy);

        List<AnswerOptionEntity> options = new ArrayList<>(
                answerOptionRepository.findByQuestionIdOrderByPosition(src.getId()));
        Collections.shuffle(options);   // xáo trộn đáp án
        int p = 1;
        for (AnswerOptionEntity src2 : options) {
            AnswerOptionEntity opt = new AnswerOptionEntity();
            opt.setQuestionId(copy.getId());
            opt.setPosition(p++);
            opt.setAnswerText(src2.getAnswerText());
            opt.setCorrect(src2.isCorrect());
            answerOptionRepository.save(opt);
        }
        return copy;
    }

    private String normalizeQuestionText(String text) {
        return text == null ? "" : text.strip().toLowerCase().replaceAll("\\s+", " ");
    }

    /** Mastery trung bình của 1 câu hỏi dựa trên các concept của nó. Dùng để sắp xếp câu yếu lên trước. */
    private double avgMastery(QuizQuestionEntity qq, Map<String, Double> masteryByName) {
        List<String> concepts = readStringList(qq.getConceptsJson());
        if (concepts.isEmpty()) return 0.5;
        return concepts.stream()
                .mapToDouble(c -> masteryByName.getOrDefault(c, 0.5))
                .average()
                .orElse(0.5);
    }

    @Transactional
    public void deleteQuiz(UUID ownerId, UUID quizId) {
        QuizEntity quiz = quizRepository.findByIdAndOwnerId(quizId, ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Quiz not found."));
        // DB cascade will handle quiz_questions, answer_options, quiz_attempts, attempt_answers
        quizRepository.delete(quiz);
    }

    private int saveGeneratedQuestion(
            QuizEntity quiz,
            QuestionBatchSpec spec,
            GeneratedQuestion generated,
            DocumentChunkEntity sourceChunk,
            int position
    ) {
        QuizQuestionEntity saved = saveGeneratedQuestionEntity(quiz, spec, generated, sourceChunk, position);
        return saved == null ? position : position + 1;
    }

    /** Tạo + lưu 1 câu hỏi từ output AI. Trả về entity, hoặc null nếu output không hợp lệ. */
    private QuizQuestionEntity saveGeneratedQuestionEntity(
            QuizEntity quiz,
            QuestionBatchSpec spec,
            GeneratedQuestion generated,
            DocumentChunkEntity sourceChunk,
            int position
    ) {
        if (generated.answer() == null || generated.answer().size() < 2
                || generated.question() == null || generated.question().isBlank()) {
            return null;
        }

        QuizQuestionEntity question = new QuizQuestionEntity();
        question.setQuizId(quiz.getId());
        question.setChunkId(sourceChunk.getId());
        question.setPosition(position);
        question.setDifficulty(spec.difficulty());
        question.setChoiceMode(spec.choiceMode());
        question.setNumCorrect(spec.numCorrect());
        question.setQuestionText(generated.question());
        List<String> concepts = generated.concepts() == null || generated.concepts().isEmpty()
                ? readStringList(sourceChunk.getConceptsJson())
                : generated.concepts();
        question.setConceptsJson(writeJson(concepts));
        quizQuestionRepository.save(question);

        int optionPosition = 1;
        for (QuestionGeneratorClient.GeneratedAnswer generatedAnswer : generated.answer()) {
            AnswerOptionEntity option = new AnswerOptionEntity();
            option.setQuestionId(question.getId());
            option.setPosition(optionPosition++);
            option.setAnswerText(generatedAnswer.answer());
            option.setCorrect(generatedAnswer.correct());
            answerOptionRepository.save(option);
        }
        return question;
    }

    private List<DocumentChunkEntity> selectChunks(UUID ownerId, UUID documentId, QuestionBatchSpec spec, PersonalizationOptions options) {
        List<DocumentChunkEntity> chunks = documentChunkRepository
                .findByDocumentIdAndQuizabilityScoreGreaterThanEqualOrderByChunkIndex(documentId, 0.45);
        if (spec.chunkIds() != null && !spec.chunkIds().isEmpty()) {
            Set<UUID> allowed = new HashSet<>(spec.chunkIds());
            chunks = chunks.stream().filter(chunk -> allowed.contains(chunk.getId())).toList();
        }
        if (chunks.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "No quizable chunks found for this batch.");
        }
        if (options.enabled() && options.focusWrongConcepts()) {
            return personalizationService.rankChunks(ownerId, documentId, chunks);
        }
        return chunks.stream()
                .sorted(Comparator.comparingDouble(DocumentChunkEntity::getQuizabilityScore).reversed())
                .toList();
    }

    private QuizQuestionResponse toQuestionResponse(QuizQuestionEntity question, List<AnswerOptionEntity> options) {
        return new QuizQuestionResponse(
                question.getId(),
                question.getChunkId(),
                question.getPosition(),
                question.getDifficulty(),
                question.getChoiceMode(),
                question.getNumCorrect(),
                question.getQuestionText(),
                readStringList(question.getConceptsJson()),
                options.stream()
                        .map(option -> new AnswerOptionResponse(option.getId(), option.getPosition(), option.getAnswerText()))
                        .toList());
    }

    private void validatePlan(List<QuestionBatchSpec> plan) {
        int total = plan.stream().mapToInt(QuestionBatchSpec::quantity).sum();
        if (total > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A quiz can contain at most 100 questions.");
        }
        for (QuestionBatchSpec spec : plan) {
            try {
                spec.validateConsistency();
            } catch (IllegalArgumentException exception) {
                throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
            }
        }
    }

    private PersonalizationOptions options(GenerateQuizRequest request) {
        return request.personalization() == null ? PersonalizationOptions.defaults() : request.personalization();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot serialize JSON.");
        }
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (IOException exception) {
            return List.of();
        }
    }
}
