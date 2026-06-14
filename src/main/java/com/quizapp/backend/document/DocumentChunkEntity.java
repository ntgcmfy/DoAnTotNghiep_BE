package com.quizapp.backend.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "document_chunks")
public class DocumentChunkEntity {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID documentId;

    @Column(nullable = false)
    private int chunkIndex;

    @Column(nullable = false)
    private int pageStart;

    @Column(nullable = false)
    private int pageEnd;

    @Column(nullable = false)
    private int wordCount;

    @Column(nullable = false)
    private double quizabilityScore;

    @Column(columnDefinition = "text")
    private String sectionPathJson;

    @Column(columnDefinition = "text")
    private String text;

    @Column(columnDefinition = "text")
    private String conceptsJson;

    @Column(columnDefinition = "text")
    private String formulasJson;

    @Column(columnDefinition = "text")
    private String codeBlocksJson;

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

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public int getPageStart() {
        return pageStart;
    }

    public void setPageStart(int pageStart) {
        this.pageStart = pageStart;
    }

    public int getPageEnd() {
        return pageEnd;
    }

    public void setPageEnd(int pageEnd) {
        this.pageEnd = pageEnd;
    }

    public int getWordCount() {
        return wordCount;
    }

    public void setWordCount(int wordCount) {
        this.wordCount = wordCount;
    }

    public double getQuizabilityScore() {
        return quizabilityScore;
    }

    public void setQuizabilityScore(double quizabilityScore) {
        this.quizabilityScore = quizabilityScore;
    }

    public String getSectionPathJson() {
        return sectionPathJson;
    }

    public void setSectionPathJson(String sectionPathJson) {
        this.sectionPathJson = sectionPathJson;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getConceptsJson() {
        return conceptsJson;
    }

    public void setConceptsJson(String conceptsJson) {
        this.conceptsJson = conceptsJson;
    }

    public String getFormulasJson() {
        return formulasJson;
    }

    public void setFormulasJson(String formulasJson) {
        this.formulasJson = formulasJson;
    }

    public String getCodeBlocksJson() {
        return codeBlocksJson;
    }

    public void setCodeBlocksJson(String codeBlocksJson) {
        this.codeBlocksJson = codeBlocksJson;
    }
}
