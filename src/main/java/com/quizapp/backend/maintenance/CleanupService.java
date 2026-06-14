package com.quizapp.backend.maintenance;

import com.quizapp.backend.flashcard.repo.FlashcardRepository;
import com.quizapp.backend.quiz.QuizEntity;
import com.quizapp.backend.quiz.repo.QuizRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodically removes content the user never saved:
 *  - loose flashcards (not placed into any collection)
 *  - unsaved quizzes (and, via DB cascade, their questions/options/attempts)
 * older than the retention window.
 */
@Service
public class CleanupService {

    private static final Logger log = LoggerFactory.getLogger(CleanupService.class);
    private static final Duration RETENTION = Duration.ofDays(7);

    private final FlashcardRepository flashcardRepository;
    private final QuizRepository quizRepository;

    public CleanupService(FlashcardRepository flashcardRepository, QuizRepository quizRepository) {
        this.flashcardRepository = flashcardRepository;
        this.quizRepository = quizRepository;
    }

    /** Runs every day at 03:00 server time. */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupUnsaved() {
        Instant cutoff = Instant.now().minus(RETENTION);
        try {
            long looseCards = flashcardRepository.deleteByCollectionIdIsNullAndCreatedAtBefore(cutoff);

            List<QuizEntity> staleQuizzes = quizRepository.findBySavedFalseAndCreatedAtBefore(cutoff);
            if (!staleQuizzes.isEmpty()) {
                quizRepository.deleteAll(staleQuizzes); // DB cascade removes questions/attempts
            }

            log.info("Cleanup done: removed {} loose flashcard(s) and {} unsaved quiz(zes) older than {} days.",
                    looseCards, staleQuizzes.size(), RETENTION.toDays());
        } catch (Exception exception) {
            // Never let a scheduled run bubble up; just log and retry next cycle.
            log.error("Cleanup job failed: {}", exception.getMessage(), exception);
        }
    }
}
