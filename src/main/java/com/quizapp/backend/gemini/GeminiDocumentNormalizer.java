package com.quizapp.backend.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizapp.backend.common.ApiException;
import com.quizapp.backend.config.AppProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class GeminiDocumentNormalizer {
    private final AppProperties properties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public GeminiDocumentNormalizer(
            AppProperties properties,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public NormalizedDocument normalize(Path filePath, String mimeType) {
        if (properties.getGemini().getApiKey() == null || properties.getGemini().getApiKey().isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "GEMINI_API_KEY is not configured.");
        }

        try {
            byte[] bytes = Files.readAllBytes(filePath);
            if (bytes.length > properties.getGemini().getInlineMaxBytes()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "File is larger than inline Gemini limit configured for this backend. Use Gemini Files API for this file.");
            }

            Map<String, Object> request = Map.of(
                    "contents", List.of(Map.of(
                            "role", "user",
                            "parts", List.of(
                                    Map.of("text", normalizationPrompt()),
                                    Map.of("inline_data", Map.of(
                                            "mime_type", mimeType,
                                            "data", Base64.getEncoder().encodeToString(bytes)))))),
                    "generationConfig", Map.of(
                            "temperature", 0.1,
                            "responseMimeType", "application/json",
                            "responseSchema", responseSchema()));

            String raw = webClient.post()
                    .uri(properties.getGemini().getGenerateUrl())
                    .header("x-goog-api-key", properties.getGemini().getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();

            String json = extractText(raw);
            return objectMapper.readValue(json, NormalizedDocument.class);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot read or parse normalized document.");
        }
    }

    private String extractText(String rawResponse) throws IOException {
        JsonNode root = objectMapper.readTree(rawResponse);
        JsonNode text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        if (text.isMissingNode() || text.asText().isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Gemini did not return normalized JSON text.");
        }
        return text.asText();
    }

    private String normalizationPrompt() {
        return """
                You are a document normalization engine for a text-based quiz generation system.
                The quiz generator works ONLY with natural language sentences.
                
                STRICT RULES:
                1. Extract document content in natural reading order.
                2. Do NOT add outside knowledge. Do NOT summarize away important content.
                3. Preserve meaning exactly. Clean layout noise but never change facts.
                4. Remove repeated headers, footers, page numbers, watermarks, and broken OCR fragments.
                
                CONTENT HANDLING:
                5. MATH FORMULAS: Keep LaTeX inline ($...$) or block ($$...$$) formulas ONLY when they
                   appear INSIDE an explanatory sentence that defines or explains a concept.
                   Example KEEP: "The entropy is defined as $H = -\\sum p \\log p$, which measures uncertainty."
                   Example REMOVE: A standalone line like "$H = -\\sum p \\log p$" with no surrounding explanation.
                6. CODE BLOCKS: Remove ALL code blocks (fenced ``` or indented). Code cannot be turned
                   into meaningful multiple-choice questions.
                7. IMAGES & DIAGRAMS: Replace with a brief neutral textual description of visible information only.
                   Do not invent content.
                8. TABLES: Convert small tables to markdown. Convert large tables to factual text rows.
                
                CHUNKING RULES:
                9. Split chunks by real sections or topics. Each chunk must be self-contained.
                10. Prefer chunks containing: definitions, concept explanations, cause-effect relationships,
                    comparisons, examples, and formulas WITH their surrounding explanation text.
                11. Assign quizabilityScore (0.0 to 1.0):
                    - Score >= 0.6: chunk is mostly natural-language sentences explaining concepts.
                    - Score 0.3-0.59: mixed content (some useful text + some formulas/code).
                    - Score < 0.3: chunk is mainly code, isolated formulas, cover page, bibliography,
                      table of contents, or navigation text with little learning value.
                
                Return JSON only following the provided schema.
                """;
    }

    private Map<String, Object> responseSchema() {
        return Map.of(
                "type", "object",
                "required", List.of("title", "language", "pages", "chunks", "extractionQuality", "warnings"),
                "properties", Map.of(
                        "title", Map.of("type", "string"),
                        "language", Map.of("type", "string"),
                        "warnings", Map.of("type", "array", "items", Map.of("type", "string")),
                        "extractionQuality", Map.of(
                                "type", "object",
                                "required", List.of("ocrConfidence", "layoutComplexity"),
                                "properties", Map.of(
                                        "ocrConfidence", Map.of("type", "string", "enum", List.of("high", "medium", "low")),
                                        "layoutComplexity", Map.of("type", "string", "enum", List.of("simple", "medium", "complex")))),
                        "pages", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "required", List.of("pageNumber", "normalizedMarkdown", "warnings", "ocrConfidence"),
                                        "properties", Map.of(
                                                "pageNumber", Map.of("type", "integer"),
                                                "normalizedMarkdown", Map.of("type", "string"),
                                                "warnings", Map.of("type", "array", "items", Map.of("type", "string")),
                                                "ocrConfidence", Map.of("type", "string", "enum", List.of("high", "medium", "low"))))),
                        "chunks", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "required", List.of("chunkIndex", "sectionPath", "pageStart", "pageEnd", "text", "keyConcepts", "formulas", "codeBlocks", "quizabilityScore"),
                                        "properties", Map.of(
                                                "chunkIndex", Map.of("type", "integer"),
                                                "sectionPath", Map.of("type", "array", "items", Map.of("type", "string")),
                                                "pageStart", Map.of("type", "integer"),
                                                "pageEnd", Map.of("type", "integer"),
                                                "text", Map.of("type", "string"),
                                                "keyConcepts", Map.of("type", "array", "items", Map.of("type", "string")),
                                                "formulas", Map.of("type", "array", "items", Map.of("type", "string")),
                                                "codeBlocks", Map.of("type", "array", "items", Map.of("type", "string")),
                                                "quizabilityScore", Map.of("type", "number", "minimum", 0, "maximum", 1))))));
    }
}
