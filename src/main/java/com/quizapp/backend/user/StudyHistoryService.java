package com.quizapp.backend.user;

import com.quizapp.backend.quiz.repo.QuizAttemptRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyHistoryService {
    private final QuizAttemptRepository quizAttemptRepository;
    private final UserConceptStatRepository userConceptStatRepository;

    public StudyHistoryService(
            QuizAttemptRepository quizAttemptRepository,
            UserConceptStatRepository userConceptStatRepository
    ) {
        this.quizAttemptRepository = quizAttemptRepository;
        this.userConceptStatRepository = userConceptStatRepository;
    }

    @Transactional(readOnly = true)
    public StudyHistoryResponse getHistory(UUID ownerId) {
        return new StudyHistoryResponse(
                quizAttemptRepository.findTop20ByOwnerIdOrderBySubmittedAtDesc(ownerId).stream()
                        .map(attempt -> new StudyHistoryResponse.AttemptSummary(
                                attempt.getId(),
                                attempt.getQuizId(),
                                attempt.getTotalQuestions(),
                                attempt.getCorrectQuestions(),
                                attempt.getScore(),
                                attempt.getSubmittedAt()))
                        .toList(),
                userConceptStatRepository.findByOwnerIdOrderByNextReviewAtAsc(ownerId).stream()
                        .map(stat -> new StudyHistoryResponse.ConceptMastery(
                                stat.getConcept(),
                                stat.getSeenCount(),
                                stat.getCorrectCount(),
                                stat.getWrongCount(),
                                stat.getMasteryScore(),
                                stat.getNextReviewAt()))
                        .toList());
    }
}
