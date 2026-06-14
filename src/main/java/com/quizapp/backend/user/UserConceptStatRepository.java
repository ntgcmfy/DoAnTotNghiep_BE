package com.quizapp.backend.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserConceptStatRepository extends JpaRepository<UserConceptStatEntity, UUID> {
    Optional<UserConceptStatEntity> findByOwnerIdAndDocumentIdAndConcept(UUID ownerId, UUID documentId, String concept);
    List<UserConceptStatEntity> findByOwnerIdOrderByNextReviewAtAsc(UUID ownerId);
    List<UserConceptStatEntity> findByOwnerIdAndDocumentIdOrderByMasteryScoreAsc(UUID ownerId, UUID documentId);
}
