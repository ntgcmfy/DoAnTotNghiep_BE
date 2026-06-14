package com.quizapp.backend.quiz.dto;

import com.quizapp.backend.quiz.ChoiceMode;
import com.quizapp.backend.quiz.Difficulty;
import java.util.List;
import java.util.UUID;

public record QuizQuestionResponse(
        UUID id,
        UUID chunkId,
        int position,
        Difficulty difficulty,
        ChoiceMode choiceMode,
        int numCorrect,
        String questionText,
        List<String> concepts,
        List<AnswerOptionResponse> options
) {
}
