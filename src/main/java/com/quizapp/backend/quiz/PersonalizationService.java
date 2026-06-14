package com.quizapp.backend.quiz;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizapp.backend.document.DocumentChunkEntity;
import com.quizapp.backend.user.UserConceptStatEntity;
import com.quizapp.backend.user.UserConceptStatRepository;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PersonalizationService {

    // Trade-off between targeting weak concepts and picking quizable content when
    // ranking chunks. priority = lambda * weakness + (1 - lambda) * quizability,
    // both terms in [0, 1]. 0.5 weights the two goals equally.
    private static final double PRIORITY_WEAKNESS_WEIGHT = 0.5;

    // Concepts with mastery below this threshold are considered still weak and
    // eligible for retry in adaptive quizzes.
    public static final double MASTERY_WEAK_THRESHOLD = 0.7;

    private final UserConceptStatRepository userConceptStatRepository;
    private final ObjectMapper objectMapper;

    public PersonalizationService(UserConceptStatRepository userConceptStatRepository, ObjectMapper objectMapper) {
        this.userConceptStatRepository = userConceptStatRepository;
        this.objectMapper = objectMapper;
    }

    /** Returns names of concepts whose mastery is still below MASTERY_WEAK_THRESHOLD. */
    public Set<String> weakConcepts(UUID ownerId, UUID documentId) {
        return userConceptStatRepository
                .findByOwnerIdAndDocumentIdOrderByMasteryScoreAsc(ownerId, documentId)
                .stream()
                .filter(s -> s.getMasteryScore() < MASTERY_WEAK_THRESHOLD)
                .map(UserConceptStatEntity::getConcept)
                .collect(Collectors.toSet());
    }

    /** Returns a map of concept name → current mastery score for this document. */
    public Map<String, Double> masteryMap(UUID ownerId, UUID documentId) {
        return userConceptStatRepository
                .findByOwnerIdAndDocumentIdOrderByMasteryScoreAsc(ownerId, documentId)
                .stream()
                .collect(Collectors.toMap(UserConceptStatEntity::getConcept, UserConceptStatEntity::getMasteryScore));
    }

    public List<DocumentChunkEntity> rankChunks(UUID ownerId, UUID documentId, List<DocumentChunkEntity> chunks) {
        List<UserConceptStatEntity> stats = userConceptStatRepository.findByOwnerIdAndDocumentIdOrderByMasteryScoreAsc(ownerId, documentId);
        return chunks.stream()
                .sorted(Comparator.comparingDouble(chunk -> -priorityScore(chunk, stats)))
                .toList();
    }

    public void recordQuestionResult(
            UUID ownerId,
            UUID documentId,
            List<String> concepts,
            boolean correct
    ) {
        Instant now = Instant.now();
        for (String concept : concepts) {
            UserConceptStatEntity stat = userConceptStatRepository
                    .findByOwnerIdAndDocumentIdAndConcept(ownerId, documentId, concept)
                    .orElseGet(() -> createStat(ownerId, documentId, concept));

            stat.setSeenCount(stat.getSeenCount() + 1);
            stat.setLastSeenAt(now);
            if (correct) {
                stat.setCorrectCount(stat.getCorrectCount() + 1);
            } else {
                stat.setWrongCount(stat.getWrongCount() + 1);
            }
            // Mastery as the posterior mean of a Beta-Bernoulli model with a
            // uniform Beta(1,1) prior (Laplace smoothing): no tuned increments.
            // A new concept starts at (0+1)/(0+2) = 0.5.
            stat.setMasteryScore((stat.getCorrectCount() + 1.0) / (stat.getSeenCount() + 2.0));
            // Review scheduling via SM-2 (Wozniak, 1990).
            applySm2(stat, correct, now);
            userConceptStatRepository.save(stat);
        }
    }

    /**
     * Updates the concept's SM-2 spaced-repetition state and next review time.
     * The binary quiz outcome is mapped to an SM-2 quality grade: a correct
     * recall is a "good" answer (q = 4, ease factor unchanged), an incorrect one
     * is a lapse (q = 2, ease factor lowered, schedule reset). All other
     * constants (initial ease 2.5, minimum ease 1.3, first intervals 1 and 6
     * days) are the published SM-2 values, not tuned parameters.
     */
    private void applySm2(UserConceptStatEntity stat, boolean correct, Instant now) {
        int quality = correct ? 4 : 2;
        double easeFactor = stat.getEaseFactor();
        int repetition = stat.getRepetition();
        int intervalDays;

        if (quality < 3) {
            repetition = 0;
            intervalDays = 1;
        } else {
            repetition += 1;
            if (repetition == 1) {
                intervalDays = 1;
            } else if (repetition == 2) {
                intervalDays = 6;
            } else {
                intervalDays = (int) Math.round(stat.getIntervalDays() * easeFactor);
            }
        }

        easeFactor += 0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02);
        if (easeFactor < 1.3) {
            easeFactor = 1.3;
        }

        stat.setEaseFactor(easeFactor);
        stat.setRepetition(repetition);
        stat.setIntervalDays(intervalDays);
        stat.setNextReviewAt(now.plus(intervalDays, ChronoUnit.DAYS));
    }

    private double priorityScore(DocumentChunkEntity chunk, List<UserConceptStatEntity> stats) {
        List<String> concepts = readConcepts(chunk.getConceptsJson());
        // Quizability is already in [0, 1] (assigned during document processing).
        double quizability = chunk.getQuizabilityScore();
        // Weakness = mean (1 - mastery) over the chunk's concepts the learner has
        // already seen, also in [0, 1]; 0 if none have been seen yet.
        double weakness = stats.stream()
                .filter(stat -> concepts.contains(stat.getConcept()))
                .mapToDouble(stat -> 1.0 - stat.getMasteryScore())
                .average()
                .orElse(0.0);
        // Convex combination of the two normalised objectives.
        return PRIORITY_WEAKNESS_WEIGHT * weakness
                + (1.0 - PRIORITY_WEAKNESS_WEIGHT) * quizability;
    }

    private UserConceptStatEntity createStat(UUID ownerId, UUID documentId, String concept) {
        UserConceptStatEntity stat = new UserConceptStatEntity();
        stat.setOwnerId(ownerId);
        stat.setDocumentId(documentId);
        stat.setConcept(concept);
        stat.setMasteryScore(0.5);
        return stat;
    }

    private List<String> readConcepts(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (IOException exception) {
            return List.of();
        }
    }
}
