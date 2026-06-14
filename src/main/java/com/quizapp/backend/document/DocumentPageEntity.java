package com.quizapp.backend.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "document_pages")
public class DocumentPageEntity {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID documentId;

    @Column(nullable = false)
    private int pageNumber;

    @Column(columnDefinition = "text")
    private String normalizedMarkdown;

    @Column(columnDefinition = "text")
    private String warningsJson;

    @Column(nullable = false)
    private String ocrConfidence;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public String getNormalizedMarkdown() {
        return normalizedMarkdown;
    }

    public void setNormalizedMarkdown(String normalizedMarkdown) {
        this.normalizedMarkdown = normalizedMarkdown;
    }

    public String getWarningsJson() {
        return warningsJson;
    }

    public void setWarningsJson(String warningsJson) {
        this.warningsJson = warningsJson;
    }

    public String getOcrConfidence() {
        return ocrConfidence;
    }

    public void setOcrConfidence(String ocrConfidence) {
        this.ocrConfidence = ocrConfidence;
    }
}
