package com.quizapp.backend.flashcard.repo;

import com.quizapp.backend.flashcard.FlashcardCollectionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlashcardCollectionRepository extends JpaRepository<FlashcardCollectionEntity, UUID> {
    List<FlashcardCollectionEntity> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
    Optional<FlashcardCollectionEntity> findByIdAndOwnerId(UUID id, UUID ownerId);
}
