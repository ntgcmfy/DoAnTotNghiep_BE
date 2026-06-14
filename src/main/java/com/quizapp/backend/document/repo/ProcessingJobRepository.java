package com.quizapp.backend.document.repo;

import com.quizapp.backend.document.ProcessingJobEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJobEntity, UUID> {
    Optional<ProcessingJobEntity> findTopByDocumentIdOrderByCreatedAtDesc(UUID documentId);
}
