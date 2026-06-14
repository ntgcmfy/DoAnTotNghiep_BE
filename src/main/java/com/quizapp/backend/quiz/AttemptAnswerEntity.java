package com.quizapp.backend.quiz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "attempt_answers")
public class AttemptAnswerEntity {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID attemptId;

    @Column(nullable = false)
    private UUID questionId;

    @Column(columnDefinition = "text")
    private String selectedOptionIdsJson;

    @Column(nullable = false)
    private boolean correct;

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

    public UUID getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(UUID attemptId) {
        this.attemptId = attemptId;
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public void setQuestionId(UUID questionId) {
        this.questionId = questionId;
    }

    public String getSelectedOptionIdsJson() {
        return selectedOptionIdsJson;
    }

    public void setSelectedOptionIdsJson(String selectedOptionIdsJson) {
        this.selectedOptionIdsJson = selectedOptionIdsJson;
    }

    public boolean isCorrect() {
        return correct;
    }

    public void setCorrect(boolean correct) {
        this.correct = correct;
    }
}
