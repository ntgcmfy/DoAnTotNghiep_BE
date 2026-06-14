package com.quizapp.backend.document;

import com.quizapp.backend.document.dto.ChunkResponse;
import com.quizapp.backend.document.dto.CreateTextDocumentRequest;
import com.quizapp.backend.document.dto.DocumentResponse;
import com.quizapp.backend.document.dto.DocumentStatusResponse;
import com.quizapp.backend.document.dto.DocumentUploadResponse;
import com.quizapp.backend.document.dto.UpdateDocumentRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public List<DocumentResponse> list(@AuthenticationPrincipal UUID userId) {
        return documentService.listDocuments(userId);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentUploadResponse upload(
            @AuthenticationPrincipal UUID userId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "pageStart", required = false) Integer pageStart,
            @RequestParam(value = "pageEnd", required = false) Integer pageEnd
    ) {
        return documentService.upload(userId, file, pageStart, pageEnd);
    }

    @PostMapping("/text")
    public DocumentUploadResponse uploadText(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody CreateTextDocumentRequest request
    ) {
        return documentService.uploadText(userId, request.title(), request.text());
    }

    @GetMapping("/{documentId}/status")
    public DocumentStatusResponse status(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID documentId
    ) {
        return documentService.getStatus(userId, documentId);
    }

    @GetMapping("/{documentId}/chunks")
    public List<ChunkResponse> chunks(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID documentId
    ) {
        return documentService.getChunks(userId, documentId);
    }

    @PatchMapping("/{documentId}")
    public DocumentResponse rename(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID documentId,
            @Valid @RequestBody UpdateDocumentRequest request
    ) {
        return documentService.updateTitle(userId, documentId, request.title());
    }

    @DeleteMapping("/{documentId}")
    public void delete(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID documentId
    ) {
        documentService.deleteDocument(userId, documentId);
    }
}
