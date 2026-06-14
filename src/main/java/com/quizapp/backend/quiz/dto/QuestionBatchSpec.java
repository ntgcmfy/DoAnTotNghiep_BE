package com.quizapp.backend.quiz.dto;

import com.quizapp.backend.quiz.ChoiceMode;
import com.quizapp.backend.quiz.Difficulty;
import com.quizapp.backend.quiz.QuestionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record QuestionBatchSpec(
        @Min(1) @Max(50) int quantity,
        @NotNull Difficulty difficulty,
        @NotNull QuestionType questionType,
        @NotNull ChoiceMode choiceMode,
        @Min(2) @Max(8) int numChoices,
        @Min(1) @Max(5) int numCorrect,
        List<UUID> chunkIds
) {
    public void validateConsistency() {
        if (numCorrect > numChoices) {
            throw new IllegalArgumentException("numCorrect cannot be greater than numChoices.");
        }
        if (choiceMode == ChoiceMode.SINGLE_CORRECT && numCorrect != 1) {
            throw new IllegalArgumentException("SINGLE_CORRECT requires numCorrect = 1.");
        }
        if (choiceMode == ChoiceMode.MULTIPLE_CORRECT && numCorrect < 2) {
            throw new IllegalArgumentException("MULTIPLE_CORRECT requires numCorrect >= 2.");
        }
    }
}
