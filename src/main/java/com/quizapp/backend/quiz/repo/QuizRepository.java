package com.quizapp.backend.quiz.repo;

import com.quizapp.backend.quiz.QuizEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizRepository extends JpaRepository<QuizEntity, UUID> {
    Optional<QuizEntity> findByIdAndOwnerId(UUID id, UUID ownerId);
    List<QuizEntity> findTop20ByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
    List<QuizEntity> findBySavedFalseAndCreatedAtBefore(Instant cutoff);
    List<QuizEntity> findByDocumentIdAndOwnerId(UUID documentId, UUID ownerId);
}
