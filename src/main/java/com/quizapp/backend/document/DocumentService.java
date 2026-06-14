package com.quizapp.backend.document;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizapp.backend.common.ApiException;
import com.quizapp.backend.config.AppProperties;
import com.quizapp.backend.document.dto.ChunkResponse;
import com.quizapp.backend.document.dto.DocumentResponse;
import com.quizapp.backend.document.dto.DocumentStatusResponse;
import com.quizapp.backend.document.dto.DocumentUploadResponse;
import com.quizapp.backend.document.processing.DocumentProcessingService;
import com.quizapp.backend.document.repo.DocumentChunkRepository;
import com.quizapp.backend.document.repo.DocumentRepository;
import com.quizapp.backend.document.repo.ProcessingJobRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {
    private static final List<String> SUPPORTED_EXTENSIONS = List.of(".pdf", ".docx", ".png", ".jpg", ".jpeg", ".webp");

    private final DocumentRepository documentRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentProcessingService documentProcessingService;
    private final com.quizapp.backend.document.processing.FileValidator fileValidator;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public DocumentService(
            DocumentRepository documentRepository,
            ProcessingJobRepository processingJobRepository,
            DocumentChunkRepository documentChunkRepository,
            DocumentProcessingService documentProcessingService,
            com.quizapp.backend.document.processing.FileValidator fileValidator,
            AppProperties properties,
            ObjectMapper objectMapper
    ) {
        this.documentRepository = documentRepository;
        this.processingJobRepository = processingJobRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.documentProcessingService = documentProcessingService;
        this.fileValidator = fileValidator;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public DocumentUploadResponse upload(UUID ownerId, MultipartFile file, Integer pageStart, Integer pageEnd) {
        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Uploaded file is empty.");
        }
        String originalFilename = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        assertSupported(originalFilename);
        // Kiểm tra magic bytes + dung lượng + khoảng trang TRƯỚC khi lưu.
        fileValidator.validate(file, originalFilename, pageStart, pageEnd);

        UUID documentId = UUID.randomUUID();
        // PDF + có chọn khoảng trang → trích trang thành PDF nhỏ rồi mới lưu/xử lý.
        Path storedPath;
        boolean isPdf = originalFilename.toLowerCase(Locale.ROOT).endsWith(".pdf");
        if (isPdf && pageStart != null && pageEnd != null) {
            byte[] subPdf = fileValidator.extractPdfPages(file, pageStart, pageEnd);
            storedPath = storeBytes(ownerId, documentId, ".pdf", subPdf);
        } else {
            storedPath = storeFile(ownerId, documentId, originalFilename, file);
        }

        DocumentEntity document = new DocumentEntity();
        document.setId(documentId);
        document.setOwnerId(ownerId);
        document.setOriginalFilename(originalFilename);
        // Default title = original filename without extension. The normalizer keeps
        // this unless it can derive a better one, and the user can rename later.
        document.setTitle(stripExtension(originalFilename));
        document.setContentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        document.setStoredPath(storedPath.toString());
        document.setStatus(DocumentStatus.PROCESSING);
        documentRepository.save(document);

        ProcessingJobEntity job = new ProcessingJobEntity();
        job.setDocumentId(documentId);
        job.setStage(ProcessingStage.STORED);
        job.setProgressPercent(5);
        job.setMessage("File stored. Processing will continue asynchronously.");
        processingJobRepository.save(job);

        documentProcessingService.processDocument(document, job);
        return new DocumentUploadResponse(documentId, job.getId(), document.getStatus());
    }

    public DocumentUploadResponse uploadText(UUID ownerId, String title, String text) {
        String content = text == null ? "" : text.strip();
        if (content.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Nội dung văn bản không được để trống.");
        }
        if (content.length() > 100_000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Nội dung quá dài (tối đa 100.000 ký tự).");
        }

        String resolvedTitle = (title != null && !title.isBlank())
                ? title.trim()
                : deriveTitleFromText(content);

        UUID documentId = UUID.randomUUID();
        Path storedPath = storeText(ownerId, documentId, content);

        DocumentEntity document = new DocumentEntity();
        document.setId(documentId);
        document.setOwnerId(ownerId);
        document.setOriginalFilename(resolvedTitle + ".txt");
        document.setTitle(resolvedTitle);
        document.setContentType("text/plain");
        document.setStoredPath(storedPath.toString());
        document.setStatus(DocumentStatus.PROCESSING);
        documentRepository.save(document);

        ProcessingJobEntity job = new ProcessingJobEntity();
        job.setDocumentId(documentId);
        job.setStage(ProcessingStage.STORED);
        job.setProgressPercent(5);
        job.setMessage("Text stored. Processing will continue asynchronously.");
        processingJobRepository.save(job);

        documentProcessingService.processDocument(document, job);
        return new DocumentUploadResponse(documentId, job.getId(), document.getStatus());
    }

    @Transactional(readOnly = true)
    public DocumentStatusResponse getStatus(UUID ownerId, UUID documentId) {
        DocumentEntity document = findOwnedDocument(ownerId, documentId);
        ProcessingJobEntity job = processingJobRepository.findTopByDocumentIdOrderByCreatedAtDesc(documentId)
                .orElse(null);

        return new DocumentStatusResponse(
                document.getId(),
                document.getTitle(),
                document.getOriginalFilename(),
                document.getStatus(),
                job == null ? null : job.getStage(),
                job == null ? 0 : job.getProgressPercent(),
                job == null ? null : job.getMessage(),
                document.getFailureReason());
    }

    @Transactional
    public DocumentResponse updateTitle(UUID ownerId, UUID documentId, String title) {
        DocumentEntity document = findOwnedDocument(ownerId, documentId);
        String trimmed = title == null ? "" : title.trim();
        if (trimmed.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Tên tài liệu không được để trống.");
        }
        if (trimmed.length() > 512) {
            trimmed = trimmed.substring(0, 512);
        }
        document.setTitle(trimmed);
        documentRepository.save(document);
        return new DocumentResponse(
                document.getId(),
                document.getOriginalFilename(),
                document.getTitle(),
                document.getLanguageCode(),
                document.getStatus(),
                document.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<ChunkResponse> getChunks(UUID ownerId, UUID documentId) {
        findOwnedDocument(ownerId, documentId);
        return documentChunkRepository.findByDocumentIdOrderByChunkIndex(documentId).stream()
                .map(this::toChunkResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listDocuments(UUID ownerId) {
        return documentRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(doc -> new DocumentResponse(
                        doc.getId(),
                        doc.getOriginalFilename(),
                        doc.getTitle(),
                        doc.getLanguageCode(),
                        doc.getStatus(),
                        doc.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void deleteDocument(UUID ownerId, UUID documentId) {
        DocumentEntity document = findOwnedDocument(ownerId, documentId);
        // DB cascade will handle processing_jobs, document_pages, document_chunks,
        // quizzes, quiz_questions (chunk_id set null), user_concept_stats
        documentRepository.delete(document);
        // Clean up physical files
        deleteStoredFiles(document);
    }

    public DocumentEntity findOwnedDocument(UUID ownerId, UUID documentId) {
        return documentRepository.findByIdAndOwnerId(documentId, ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found."));
    }

    private ChunkResponse toChunkResponse(DocumentChunkEntity chunk) {
        return new ChunkResponse(
                chunk.getId(),
                chunk.getChunkIndex(),
                chunk.getPageStart(),
                chunk.getPageEnd(),
                chunk.getWordCount(),
                chunk.getQuizabilityScore(),
                readStringList(chunk.getSectionPathJson()),
                readStringList(chunk.getConceptsJson()),
                chunk.getText());
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (IOException exception) {
            return List.of();
        }
    }

    private Path storeFile(UUID ownerId, UUID documentId, String originalFilename, MultipartFile file) {
        String extension = extension(originalFilename);
        Path ownerDir = Path.of(properties.getStorage().getRoot(), ownerId.toString(), documentId.toString());
        try {
            Files.createDirectories(ownerDir);
            Path storedPath = ownerDir.resolve("original" + extension).normalize();
            file.transferTo(storedPath);
            return storedPath;
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot store uploaded file.");
        }
    }

    private Path storeBytes(UUID ownerId, UUID documentId, String extension, byte[] bytes) {
        Path ownerDir = Path.of(properties.getStorage().getRoot(), ownerId.toString(), documentId.toString());
        try {
            Files.createDirectories(ownerDir);
            Path storedPath = ownerDir.resolve("original" + extension).normalize();
            Files.write(storedPath, bytes);
            return storedPath;
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot store extracted file.");
        }
    }

    private Path storeText(UUID ownerId, UUID documentId, String text) {
        Path ownerDir = Path.of(properties.getStorage().getRoot(), ownerId.toString(), documentId.toString());
        try {
            Files.createDirectories(ownerDir);
            Path storedPath = ownerDir.resolve("original.txt").normalize();
            Files.writeString(storedPath, text, StandardCharsets.UTF_8);
            return storedPath;
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Không lưu được nội dung văn bản.");
        }
    }

    private String deriveTitleFromText(String text) {
        String firstLine = text.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .findFirst()
                .orElse("Văn bản dán");
        firstLine = firstLine.replaceAll("^#+\\s*", ""); // strip markdown heading markers
        if (firstLine.length() > 80) {
            firstLine = firstLine.substring(0, 80).trim() + "...";
        }
        return firstLine.isBlank() ? "Văn bản dán" : firstLine;
    }

    private void assertSupported(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (SUPPORTED_EXTENSIONS.stream().noneMatch(lower::endsWith)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported file type. Use PDF, DOCX, PNG, JPG, JPEG, or WEBP.");
        }
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase(Locale.ROOT) : "";
    }

    private String stripExtension(String filename) {
        String name = filename;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        name = name.trim();
        return name.isEmpty() ? filename : name;
    }

    private void deleteStoredFiles(DocumentEntity document) {
        try {
            Path storedPath = Path.of(document.getStoredPath());
            Files.deleteIfExists(storedPath);
            if (document.getNormalizedPdfPath() != null) {
                Files.deleteIfExists(Path.of(document.getNormalizedPdfPath()));
            }
            // Try to remove the document directory if empty
            Path parentDir = storedPath.getParent();
            if (parentDir != null && Files.isDirectory(parentDir)) {
                try (var entries = Files.list(parentDir)) {
                    if (entries.findFirst().isEmpty()) {
                        Files.delete(parentDir);
                    }
                }
            }
        } catch (IOException exception) {
            // Best-effort cleanup; the DB records are already deleted
        }
    }
}
