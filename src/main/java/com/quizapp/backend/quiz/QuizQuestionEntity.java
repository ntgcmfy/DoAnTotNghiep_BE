package com.quizapp.backend.quiz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "quiz_questions")
public class QuizQuestionEntity {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID quizId;

    @Column(nullable = true)
    private UUID chunkId;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChoiceMode choiceMode;

    @Column(nullable = false)
    private int numCorrect;

    @Column(columnDefinition = "text")
    private String questionText;

    @Column(columnDefinition = "text")
    private String conceptsJson;

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

    public UUID getQuizId() {
        return quizId;
    }

    public void setQuizId(UUID quizId) {
        this.quizId = quizId;
    }

    public UUID getChunkId() {
        return chunkId;
    }

    public void setChunkId(UUID chunkId) {
        this.chunkId = chunkId;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public ChoiceMode getChoiceMode() {
        return choiceMode;
    }

    public void setChoiceMode(ChoiceMode choiceMode) {
        this.choiceMode = choiceMode;
    }

    public int getNumCorrect() {
        return numCorrect;
    }

    public void setNumCorrect(int numCorrect) {
        this.numCorrect = numCorrect;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public String getConceptsJson() {
        return conceptsJson;
    }

    public void setConceptsJson(String conceptsJson) {
        this.conceptsJson = conceptsJson;
    }
}
