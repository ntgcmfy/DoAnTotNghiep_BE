package com.quizapp.backend.document.repo;

import com.quizapp.backend.document.DocumentEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {
    Optional<DocumentEntity> findByIdAndOwnerId(UUID id, UUID ownerId);
    List<DocumentEntity> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
