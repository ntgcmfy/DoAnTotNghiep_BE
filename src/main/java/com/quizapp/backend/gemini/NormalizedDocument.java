package com.quizapp.backend.gemini;

import java.util.List;

public record NormalizedDocument(
        String title,
        String language,
        List<NormalizedPage> pages,
        List<NormalizedChunk> chunks,
        ExtractionQuality extractionQuality,
        List<String> warnings
) {
    public record NormalizedPage(
            int pageNumber,
            String normalizedMarkdown,
            List<String> warnings,
            String ocrConfidence
    ) {
    }

    public record NormalizedChunk(
            int chunkIndex,
            List<String> sectionPath,
            int pageStart,
            int pageEnd,
            String text,
            List<String> keyConcepts,
            List<String> formulas,
            List<String> codeBlocks,
            double quizabilityScore
    ) {
    }

    public record ExtractionQuality(
            String ocrConfidence,
            String layoutComplexity
    ) {
    }
}
