package com.quizapp.backend.document.repo;

import com.quizapp.backend.document.DocumentPageEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentPageRepository extends JpaRepository<DocumentPageEntity, UUID> {
    List<DocumentPageEntity> findByDocumentIdOrderByPageNumber(UUID documentId);
    void deleteByDocumentId(UUID documentId);
}
