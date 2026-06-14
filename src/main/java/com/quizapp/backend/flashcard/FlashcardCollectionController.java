package com.quizapp.backend.flashcard;

import com.quizapp.backend.flashcard.dto.CollectionRequest;
import com.quizapp.backend.flashcard.dto.CollectionResponse;
import com.quizapp.backend.flashcard.dto.FlashcardResponse;
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
@RequestMapping("/api/flashcard-collections")
public class FlashcardCollectionController {

    private final FlashcardService flashcardService;

    public FlashcardCollectionController(FlashcardService flashcardService) {
        this.flashcardService = flashcardService;
    }

    @GetMapping
    public List<CollectionResponse> list(@AuthenticationPrincipal UUID userId) {
        return flashcardService.listCollections(userId);
    }

    @PostMapping
    public CollectionResponse create(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody CollectionRequest request
    ) {
        return flashcardService.createCollection(userId, request.name());
    }

    @PatchMapping("/{collectionId}")
    public CollectionResponse rename(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID collectionId,
            @Valid @RequestBody CollectionRequest request
    ) {
        return flashcardService.renameCollection(userId, collectionId, request.name());
    }

    @DeleteMapping("/{collectionId}")
    public void delete(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID collectionId
    ) {
        flashcardService.deleteCollection(userId, collectionId);
    }

    @GetMapping("/{collectionId}/cards")
    public List<FlashcardResponse> cards(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID collectionId
    ) {
        return flashcardService.listCollectionCards(userId, collectionId);
    }
}
