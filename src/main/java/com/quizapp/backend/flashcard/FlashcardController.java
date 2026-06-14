package com.quizapp.backend.flashcard;

import com.quizapp.backend.flashcard.dto.AddFromQuestionRequest;
import com.quizapp.backend.flashcard.dto.AddFromQuizRequest;
import com.quizapp.backend.flashcard.dto.CreateFlashcardRequest;
import com.quizapp.backend.flashcard.dto.FlashcardResponse;
import com.quizapp.backend.flashcard.dto.MoveFlashcardRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flashcards")
public class FlashcardController {

    private final FlashcardService flashcardService;

    public FlashcardController(FlashcardService flashcardService) {
        this.flashcardService = flashcardService;
    }

    @GetMapping("/loose")
    public List<FlashcardResponse> loose(@AuthenticationPrincipal UUID userId) {
        return flashcardService.listLooseCards(userId);
    }

    @GetMapping("/question/{questionId}/collections")
    public List<UUID> containingCollections(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID questionId
    ) {
        return flashcardService.collectionsContainingQuestion(userId, questionId);
    }

    @PostMapping
    public FlashcardResponse create(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody CreateFlashcardRequest request
    ) {
        return flashcardService.createCard(userId, request);
    }

    @PostMapping("/from-question")
    public FlashcardResponse fromQuestion(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody AddFromQuestionRequest request
    ) {
        return flashcardService.addFromQuestion(userId, request.questionId(), request.collectionId());
    }

    @PostMapping("/from-quiz")
    public List<FlashcardResponse> fromQuiz(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody AddFromQuizRequest request
    ) {
        return flashcardService.addFromQuiz(userId, request.quizId(), request.collectionId());
    }

    @PatchMapping("/{cardId}/move")
    public FlashcardResponse move(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID cardId,
            @RequestBody MoveFlashcardRequest request
    ) {
        return flashcardService.moveCard(userId, cardId, request.collectionId());
    }

    @DeleteMapping("/{cardId}")
    public void delete(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID cardId
    ) {
        flashcardService.deleteCard(userId, cardId);
    }
}
