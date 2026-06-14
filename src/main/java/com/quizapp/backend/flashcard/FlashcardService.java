package com.quizapp.backend.flashcard;

import com.quizapp.backend.common.ApiException;
import com.quizapp.backend.flashcard.dto.CollectionResponse;
import com.quizapp.backend.flashcard.dto.CreateFlashcardRequest;
import com.quizapp.backend.flashcard.dto.FlashcardResponse;
import com.quizapp.backend.flashcard.repo.FlashcardCollectionRepository;
import com.quizapp.backend.flashcard.repo.FlashcardRepository;
import com.quizapp.backend.quiz.AnswerOptionEntity;
import com.quizapp.backend.quiz.QuizEntity;
import com.quizapp.backend.quiz.QuizQuestionEntity;
import com.quizapp.backend.quiz.repo.AnswerOptionRepository;
import com.quizapp.backend.quiz.repo.QuizQuestionRepository;
import com.quizapp.backend.quiz.repo.QuizRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlashcardService {

    private final FlashcardCollectionRepository collectionRepository;
    private final FlashcardRepository flashcardRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final AnswerOptionRepository answerOptionRepository;

    public FlashcardService(
            FlashcardCollectionRepository collectionRepository,
            FlashcardRepository flashcardRepository,
            QuizRepository quizRepository,
            QuizQuestionRepository quizQuestionRepository,
            AnswerOptionRepository answerOptionRepository
    ) {
        this.collectionRepository = collectionRepository;
        this.flashcardRepository = flashcardRepository;
        this.quizRepository = quizRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.answerOptionRepository = answerOptionRepository;
    }

    // ── Collections ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CollectionResponse> listCollections(UUID ownerId) {
        return collectionRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(this::toCollectionResponse)
                .toList();
    }

    @Transactional
    public CollectionResponse createCollection(UUID ownerId, String name) {
        String trimmed = requireName(name);
        FlashcardCollectionEntity collection = new FlashcardCollectionEntity();
        collection.setOwnerId(ownerId);
        collection.setName(trimmed);
        collectionRepository.save(collection);
        return toCollectionResponse(collection);
    }

    @Transactional
    public CollectionResponse renameCollection(UUID ownerId, UUID collectionId, String name) {
        FlashcardCollectionEntity collection = ownedCollection(ownerId, collectionId);
        collection.setName(requireName(name));
        collectionRepository.save(collection);
        return toCollectionResponse(collection);
    }

    @Transactional
    public void deleteCollection(UUID ownerId, UUID collectionId) {
        FlashcardCollectionEntity collection = ownedCollection(ownerId, collectionId);
        collectionRepository.delete(collection); // cascade deletes its flashcards
    }

    @Transactional(readOnly = true)
    public List<FlashcardResponse> listCollectionCards(UUID ownerId, UUID collectionId) {
        ownedCollection(ownerId, collectionId);
        return flashcardRepository.findByCollectionIdOrderByCreatedAtDesc(collectionId).stream()
                .map(this::toCardResponse)
                .toList();
    }

    // ── Flashcards ──────────────────────────────────────────────────────────────

    /** Collection IDs that already contain a card derived from this question (matches dedup rules). */
    @Transactional(readOnly = true)
    public List<UUID> collectionsContainingQuestion(UUID ownerId, UUID questionId) {
        return flashcardRepository.findByOwnerIdAndCollectionIdIsNotNull(ownerId).stream()
                .filter(c -> questionId.equals(c.getSourceQuestionId()))
                .map(FlashcardEntity::getCollectionId)
                .distinct()
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FlashcardResponse> listLooseCards(UUID ownerId) {
        return flashcardRepository.findByOwnerIdAndCollectionIdIsNullOrderByCreatedAtDesc(ownerId).stream()
                .map(this::toCardResponse)
                .toList();
    }

    @Transactional
    public FlashcardResponse createCard(UUID ownerId, CreateFlashcardRequest request) {
        String front = requireText(request.front(), "Mặt trước");
        String back = requireText(request.back(), "Mặt sau");
        if (request.collectionId() != null) {
            ownedCollection(ownerId, request.collectionId());
            // Dedup manual cards by identical front within the same collection.
            Optional<FlashcardEntity> existing =
                    flashcardRepository.findFirstByCollectionIdAndFront(request.collectionId(), front);
            if (existing.isPresent()) {
                return toCardResponse(existing.get());
            }
        }
        FlashcardEntity card = new FlashcardEntity();
        card.setOwnerId(ownerId);
        card.setCollectionId(request.collectionId());
        card.setFront(front);
        card.setBack(back);
        flashcardRepository.save(card);
        return toCardResponse(card);
    }

    @Transactional
    public FlashcardResponse addFromQuestion(UUID ownerId, UUID questionId, UUID collectionId) {
        if (collectionId != null) {
            ownedCollection(ownerId, collectionId);
        }
        QuizQuestionEntity question = ownedQuestion(ownerId, questionId);
        // No duplicate of the same source question inside the same collection.
        if (collectionId != null) {
            Optional<FlashcardEntity> existing =
                    flashcardRepository.findFirstByCollectionIdAndSourceQuestionId(collectionId, questionId);
            if (existing.isPresent()) {
                return toCardResponse(existing.get());
            }
        }
        FlashcardEntity card = cardFromQuestion(ownerId, question, collectionId);
        flashcardRepository.save(card);
        return toCardResponse(card);
    }

    @Transactional
    public List<FlashcardResponse> addFromQuiz(UUID ownerId, UUID quizId, UUID collectionId) {
        if (collectionId != null) {
            ownedCollection(ownerId, collectionId);
        }
        QuizEntity quiz = quizRepository.findByIdAndOwnerId(quizId, ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Quiz không tồn tại."));
        List<QuizQuestionEntity> questions = quizQuestionRepository.findByQuizIdOrderByPosition(quiz.getId());
        if (questions.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "Quiz chưa có câu hỏi nào để chuyển thành flashcard.");
        }
        // Source questions already present in the target collection are skipped.
        Set<UUID> existingQuestionIds = collectionId == null ? Set.of()
                : flashcardRepository.findByCollectionIdOrderByCreatedAtDesc(collectionId).stream()
                        .map(FlashcardEntity::getSourceQuestionId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        List<FlashcardResponse> created = new ArrayList<>();
        for (QuizQuestionEntity question : questions) {
            if (existingQuestionIds.contains(question.getId())) {
                continue;
            }
            FlashcardEntity card = cardFromQuestion(ownerId, question, collectionId);
            flashcardRepository.save(card);
            created.add(toCardResponse(card));
        }
        return created;
    }

    @Transactional
    public FlashcardResponse moveCard(UUID ownerId, UUID cardId, UUID collectionId) {
        FlashcardEntity card = flashcardRepository.findByIdAndOwnerId(cardId, ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Flashcard không tồn tại."));
        if (collectionId != null) {
            ownedCollection(ownerId, collectionId);
            // Prevent moving a question/card that already exists in the destination collection.
            Optional<FlashcardEntity> duplicate = card.getSourceQuestionId() != null
                    ? flashcardRepository.findFirstByCollectionIdAndSourceQuestionId(collectionId, card.getSourceQuestionId())
                    : flashcardRepository.findFirstByCollectionIdAndFront(collectionId, card.getFront());
            if (duplicate.isPresent() && !duplicate.get().getId().equals(card.getId())) {
                throw new ApiException(HttpStatus.CONFLICT, "Câu này đã có trong bộ thẻ.");
            }
        }
        card.setCollectionId(collectionId);
        flashcardRepository.save(card);
        return toCardResponse(card);
    }

    @Transactional
    public void deleteCard(UUID ownerId, UUID cardId) {
        FlashcardEntity card = flashcardRepository.findByIdAndOwnerId(cardId, ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Flashcard không tồn tại."));
        flashcardRepository.delete(card);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private FlashcardEntity cardFromQuestion(UUID ownerId, QuizQuestionEntity question, UUID collectionId) {
        String back = answerOptionRepository.findByQuestionIdOrderByPosition(question.getId()).stream()
                .filter(AnswerOptionEntity::isCorrect)
                .map(AnswerOptionEntity::getAnswerText)
                .collect(Collectors.joining("\n"));
        if (back.isBlank()) {
            back = "(Chưa có đáp án)";
        }
        FlashcardEntity card = new FlashcardEntity();
        card.setOwnerId(ownerId);
        card.setCollectionId(collectionId);
        card.setFront(question.getQuestionText() == null ? "(Câu hỏi trống)" : question.getQuestionText());
        card.setBack(back);
        card.setSourceQuestionId(question.getId());
        return card;
    }

    private QuizQuestionEntity ownedQuestion(UUID ownerId, UUID questionId) {
        QuizQuestionEntity question = quizQuestionRepository.findById(questionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Câu hỏi không tồn tại."));
        QuizEntity quiz = quizRepository.findByIdAndOwnerId(question.getQuizId(), ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Không có quyền truy cập câu hỏi này."));
        // quiz fetched purely to assert ownership
        if (!quiz.getOwnerId().equals(ownerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Không có quyền truy cập câu hỏi này.");
        }
        return question;
    }

    private FlashcardCollectionEntity ownedCollection(UUID ownerId, UUID collectionId) {
        return collectionRepository.findByIdAndOwnerId(collectionId, ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bộ thẻ không tồn tại."));
    }

    private String requireName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Tên bộ thẻ không được để trống.");
        }
        return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
    }

    private String requireText(String value, String field) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, field + " không được để trống.");
        }
        return trimmed;
    }

    private CollectionResponse toCollectionResponse(FlashcardCollectionEntity collection) {
        return new CollectionResponse(
                collection.getId(),
                collection.getName(),
                flashcardRepository.countByCollectionId(collection.getId()),
                collection.getCreatedAt());
    }

    private FlashcardResponse toCardResponse(FlashcardEntity card) {
        return new FlashcardResponse(
                card.getId(),
                card.getCollectionId(),
                card.getFront(),
                card.getBack(),
                card.getSourceQuestionId(),
                card.getSourceDocumentId(),
                card.getCreatedAt());
    }
}
