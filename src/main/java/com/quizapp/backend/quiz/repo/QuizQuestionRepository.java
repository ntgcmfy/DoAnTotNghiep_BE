package com.quizapp.backend.quiz.repo;

import com.quizapp.backend.quiz.QuizQuestionEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestionEntity, UUID> {
    List<QuizQuestionEntity> findByQuizIdOrderByPosition(UUID quizId);
    int countByQuizId(UUID quizId);
}
