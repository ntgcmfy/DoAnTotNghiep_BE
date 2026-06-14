package com.quizapp.backend.quiz.repo;

import com.quizapp.backend.quiz.QuizAttemptEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAttemptRepository extends JpaRepository<QuizAttemptEntity, UUID> {
    List<QuizAttemptEntity> findTop20ByOwnerIdOrderBySubmittedAtDesc(UUID ownerId);
    java.util.Optional<QuizAttemptEntity> findFirstByQuizIdAndOwnerIdOrderBySubmittedAtDesc(UUID quizId, UUID ownerId);
}
