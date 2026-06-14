package com.quizapp.backend.quiz.dto;

public record PersonalizationOptions(
        boolean enabled,
        boolean focusWrongConcepts,
        boolean avoidRecentQuestions
) {
    public static PersonalizationOptions defaults() {
        return new PersonalizationOptions(true, true, true);
    }
}
