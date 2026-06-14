package com.quizapp.backend.document.repo;

import com.quizapp.backend.document.DocumentChunkEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunkEntity, UUID> {
    List<DocumentChunkEntity> findByDocumentIdOrderByChunkIndex(UUID documentId);
    List<DocumentChunkEntity> findByDocumentIdAndQuizabilityScoreGreaterThanEqualOrderByChunkIndex(UUID documentId, double minScore);
    void deleteByDocumentId(UUID documentId);
}
