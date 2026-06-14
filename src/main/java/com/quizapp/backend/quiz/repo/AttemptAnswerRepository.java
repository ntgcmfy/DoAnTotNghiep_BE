package com.quizapp.backend.quiz.repo;

import com.quizapp.backend.quiz.AttemptAnswerEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswerEntity, UUID> {
    List<AttemptAnswerEntity> findByAttemptId(UUID attemptId);
}
