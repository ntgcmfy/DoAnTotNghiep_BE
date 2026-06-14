package com.quizapp.backend.flashcard.repo;

import com.quizapp.backend.flashcard.FlashcardEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlashcardRepository extends JpaRepository<FlashcardEntity, UUID> {
    List<FlashcardEntity> findByCollectionIdOrderByCreatedAtDesc(UUID collectionId);
    List<FlashcardEntity> findByOwnerIdAndCollectionIdIsNullOrderByCreatedAtDesc(UUID ownerId);
    List<FlashcardEntity> findByOwnerIdAndCollectionIdIsNotNull(UUID ownerId);
    Optional<FlashcardEntity> findByIdAndOwnerId(UUID id, UUID ownerId);
    int countByCollectionId(UUID collectionId);
    long deleteByCollectionIdIsNullAndCreatedAtBefore(Instant cutoff);

    // Duplicate detection within a collection
    Optional<FlashcardEntity> findFirstByCollectionIdAndSourceQuestionId(UUID collectionId, UUID sourceQuestionId);
    Optional<FlashcardEntity> findFirstByCollectionIdAndFront(UUID collectionId, String front);
}
