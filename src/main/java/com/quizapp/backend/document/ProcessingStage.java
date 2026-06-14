package com.quizapp.backend.document;

public enum ProcessingStage {
    STORED,
    CONVERTED,
    GEMINI_NORMALIZED,
    CHUNKED,
    COMPLETED,
    FAILED
}
