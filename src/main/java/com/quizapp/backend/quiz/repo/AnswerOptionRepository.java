package com.quizapp.backend.quiz.repo;

import com.quizapp.backend.quiz.AnswerOptionEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerOptionRepository extends JpaRepository<AnswerOptionEntity, UUID> {
    List<AnswerOptionEntity> findByQuestionIdOrderByPosition(UUID questionId);
    List<AnswerOptionEntity> findByQuestionIdInOrderByQuestionIdAscPositionAsc(Collection<UUID> questionIds);
}
