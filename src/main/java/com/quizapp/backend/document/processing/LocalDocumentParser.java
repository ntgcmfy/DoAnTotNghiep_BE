package com.quizapp.backend.document.processing;

import com.quizapp.backend.gemini.NormalizedDocument;
import java.nio.file.Path;

public interface LocalDocumentParser {
    
    /**
     * Parse document locally.
     * @throws ComplexDocumentException if the document is too complex and requires Gemini fallback.
     * @throws DocumentTooLargeException if the document exceeds the page limit.
     */
    NormalizedDocument parse(Path filePath) throws Exception;
    
    boolean supports(String mimeType, String filename);
}
