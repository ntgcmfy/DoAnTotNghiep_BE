package com.quizapp.backend.document.processing;

import java.nio.file.Path;

public record NormalizedInputFile(
        Path path,
        String mimeType
) {
}
