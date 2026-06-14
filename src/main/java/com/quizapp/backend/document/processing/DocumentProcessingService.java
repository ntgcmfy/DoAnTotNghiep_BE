package com.quizapp.backend.document.processing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizapp.backend.document.DocumentChunkEntity;
import com.quizapp.backend.document.DocumentEntity;
import com.quizapp.backend.document.DocumentPageEntity;
import com.quizapp.backend.document.DocumentStatus;
import com.quizapp.backend.document.ProcessingJobEntity;
import com.quizapp.backend.document.ProcessingStage;
import com.quizapp.backend.document.repo.DocumentChunkRepository;
import com.quizapp.backend.document.repo.DocumentPageRepository;
import com.quizapp.backend.document.repo.DocumentRepository;
import com.quizapp.backend.document.repo.ProcessingJobRepository;
import com.quizapp.backend.gemini.GeminiDocumentNormalizer;
import com.quizapp.backend.gemini.NormalizedDocument;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentProcessingService {
    private final DocumentRepository documentRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final DocumentPageRepository documentPageRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentConversionService conversionService;
    private final GeminiDocumentNormalizer geminiDocumentNormalizer;
    private final ObjectMapper objectMapper;
    private final List<LocalDocumentParser> localParsers;

    public DocumentProcessingService(
            DocumentRepository documentRepository,
            ProcessingJobRepository processingJobRepository,
            DocumentPageRepository documentPageRepository,
            DocumentChunkRepository documentChunkRepository,
            DocumentConversionService conversionService,
            GeminiDocumentNormalizer geminiDocumentNormalizer,
            ObjectMapper objectMapper,
            List<LocalDocumentParser> localParsers
    ) {
        this.documentRepository = documentRepository;
        this.processingJobRepository = processingJobRepository;
        this.documentPageRepository = documentPageRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.conversionService = conversionService;
        this.geminiDocumentNormalizer = geminiDocumentNormalizer;
        this.objectMapper = objectMapper;
        this.localParsers = localParsers;
    }

    /**
     * Runs asynchronously in a separate thread.
     * Each DB-writing step uses its own short transaction via helper methods
     * so the DB connection is NOT held during the long-running Gemini API call.
     */
    @Async("documentProcessingExecutor")
    public void processDocument(DocumentEntity document, ProcessingJobEntity job) {
        try {
            updateJob(job.getId(), ProcessingStage.CONVERTED, 10, "Starting local parsing...");

            Path filePath = Path.of(document.getStoredPath());
            String filename = document.getOriginalFilename();
            String mimeType = "application/octet-stream";

            // Pasted text is already clean: send straight to Gemini as text/plain,
            // skipping local parsers and file conversion.
            if (isPlainText(document.getContentType(), filename)) {
                updateJob(job.getId(), ProcessingStage.GEMINI_NORMALIZED, 45, "Normalizing pasted text with Gemini.");
                NormalizedDocument textNormalized = geminiDocumentNormalizer.normalize(filePath, "text/plain");
                updateJob(job.getId(), ProcessingStage.CHUNKED, 80, "Saving normalized pages and chunks.");
                saveNormalizedResult(document.getId(), filePath.toString(), textNormalized);
                updateJob(job.getId(), ProcessingStage.COMPLETED, 100, "Document is ready for quiz generation.");
                return;
            }

            NormalizedDocument normalized = null;

            for (LocalDocumentParser parser : localParsers) {
                if (parser.supports(mimeType, filename)) {
                    try {
                        normalized = parser.parse(filePath);
                        updateJob(job.getId(), ProcessingStage.GEMINI_NORMALIZED, 50, "Local parsing succeeded.");
                        break;
                    } catch (ComplexDocumentException e) {
                        updateJob(job.getId(), ProcessingStage.CONVERTED, 20, "Document too complex for local parsing. Falling back to Gemini: " + e.getMessage());
                        normalized = null; 
                        break;
                    }
                }
            }

            if (normalized == null) {
                updateJob(job.getId(), ProcessingStage.CONVERTED, 25, "Preparing file for Gemini...");
                NormalizedInputFile input = conversionService.prepareForGemini(document);

                updateJob(job.getId(), ProcessingStage.GEMINI_NORMALIZED, 45, "Normalizing document with Gemini.");
                normalized = geminiDocumentNormalizer.normalize(input.path(), input.mimeType());
                filePath = input.path();
            }

            updateJob(job.getId(), ProcessingStage.CHUNKED, 80, "Saving normalized pages and chunks.");
            saveNormalizedResult(document.getId(), filePath.toString(), normalized);

            updateJob(job.getId(), ProcessingStage.COMPLETED, 100, "Document is ready for quiz generation.");
        } catch (DocumentTooLargeException e) {
            markFailed(document.getId(), job.getId(), e.getMessage());
        } catch (Throwable throwable) {
            markFailed(document.getId(), job.getId(), throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName());
        }
    }

    @Transactional
    protected void saveNormalizedResult(UUID documentId, String normalizedPdfPath, NormalizedDocument normalized) throws JsonProcessingException {
        DocumentEntity document = documentRepository.findById(documentId).orElseThrow();

        document.setNormalizedPdfPath(normalizedPdfPath);
        // Keep the existing title (defaults to the uploaded filename, or a user rename).
        // Only fall back to the normalizer's title when none is set yet.
        String currentTitle = document.getTitle();
        String normalizedTitle = normalized.title() == null ? null : normalized.title().trim();
        if ((currentTitle == null || currentTitle.isBlank())
                && normalizedTitle != null && !normalizedTitle.isEmpty()) {
            document.setTitle(normalizedTitle);
        }
        document.setLanguageCode(normalized.language());
        document.setStatus(DocumentStatus.READY);
        document.setFailureReason(null);
        documentRepository.save(document);

        documentPageRepository.deleteByDocumentId(documentId);
        documentChunkRepository.deleteByDocumentId(documentId);

        for (NormalizedDocument.NormalizedPage page : nullToEmpty(normalized.pages())) {
            DocumentPageEntity entity = new DocumentPageEntity();
            entity.setDocumentId(documentId);
            entity.setPageNumber(page.pageNumber());
            entity.setNormalizedMarkdown(sanitize(page.normalizedMarkdown()));
            entity.setWarningsJson(writeJson(page.warnings()));
            entity.setOcrConfidence(page.ocrConfidence());
            documentPageRepository.save(entity);
        }

        for (NormalizedDocument.NormalizedChunk chunk : nullToEmpty(normalized.chunks())) {
            DocumentChunkEntity entity = new DocumentChunkEntity();
            entity.setDocumentId(documentId);
            entity.setChunkIndex(chunk.chunkIndex());
            entity.setPageStart(chunk.pageStart());
            entity.setPageEnd(chunk.pageEnd());
            entity.setText(sanitize(chunk.text()));
            entity.setWordCount(chunk.text() == null ? 0 : chunk.text().split("\\s+").length);
            entity.setQuizabilityScore(chunk.quizabilityScore());
            entity.setSectionPathJson(writeJson(chunk.sectionPath()));
            entity.setConceptsJson(writeJson(chunk.keyConcepts()));
            entity.setFormulasJson(writeJson(chunk.formulas()));
            entity.setCodeBlocksJson(writeJson(chunk.codeBlocks()));
            documentChunkRepository.save(entity);
        }
    }

    @Transactional
    protected void updateJob(UUID jobId, ProcessingStage stage, int progress, String message) {
        ProcessingJobEntity job = processingJobRepository.findById(jobId).orElseThrow();
        job.setStage(stage);
        job.setProgressPercent(progress);
        job.setMessage(message);
        processingJobRepository.save(job);
    }

    @Transactional
    protected void markFailed(UUID documentId, UUID jobId, String reason) {
        if (reason != null && reason.length() > 250) {
            reason = reason.substring(0, 250) + "...";
        }
        DocumentEntity document = documentRepository.findById(documentId).orElseThrow();
        document.setStatus(DocumentStatus.FAILED);
        document.setFailureReason(reason);
        documentRepository.save(document);
        updateJob(jobId, ProcessingStage.FAILED, 100, reason);
    }

    private String writeJson(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value == null ? List.of() : value);
    }

    private <T> List<T> nullToEmpty(List<T> value) {
        return value == null ? List.of() : value;
    }

    private boolean isPlainText(String contentType, String filename) {
        if (contentType != null && contentType.toLowerCase().startsWith("text/")) {
            return true;
        }
        return filename != null && filename.toLowerCase().endsWith(".txt");
    }

    private String sanitize(String text) {
        if (text == null) return null;
        return text.replace("\u0000", "");
    }
}
