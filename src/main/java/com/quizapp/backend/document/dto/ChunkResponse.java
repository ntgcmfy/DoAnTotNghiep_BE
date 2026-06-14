package com.quizapp.backend.document.dto;

import java.util.List;
import java.util.UUID;

public record ChunkResponse(
        UUID id,
        int chunkIndex,
        int pageStart,
        int pageEnd,
        int wordCount,
        double quizabilityScore,
        List<String> sectionPath,
        List<String> concepts,
        String text
) {
}
