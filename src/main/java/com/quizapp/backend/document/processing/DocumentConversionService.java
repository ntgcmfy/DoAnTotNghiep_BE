package com.quizapp.backend.document.processing;

import com.quizapp.backend.common.ApiException;
import com.quizapp.backend.document.DocumentEntity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DocumentConversionService {
    public NormalizedInputFile prepareForGemini(DocumentEntity document) {
        Path storedPath = Path.of(document.getStoredPath());
        String lowerName = document.getOriginalFilename().toLowerCase(Locale.ROOT);

        if (lowerName.endsWith(".pdf")) {
            return new NormalizedInputFile(storedPath, "application/pdf");
        }
        if (lowerName.endsWith(".png")) {
            return new NormalizedInputFile(storedPath, "image/png");
        }
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            return new NormalizedInputFile(storedPath, "image/jpeg");
        }
        if (lowerName.endsWith(".webp")) {
            return new NormalizedInputFile(storedPath, "image/webp");
        }
        if (lowerName.endsWith(".docx")) {
            return convertDocxToPdf(storedPath);
        }

        throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported file type. Use PDF, DOCX, PNG, JPG, JPEG, or WEBP.");
    }

    private NormalizedInputFile convertDocxToPdf(Path docxPath) {
        String sofficePath = System.getenv().getOrDefault("SOFFICE_PATH", "soffice");
        Path outputDir = docxPath.getParent();
        String pdfName = stripExtension(docxPath.getFileName().toString()) + ".pdf";
        Path pdfPath = outputDir.resolve(pdfName);

        try {
            Process process = new ProcessBuilder(
                    sofficePath,
                    "--headless",
                    "--convert-to",
                    "pdf",
                    "--outdir",
                    outputDir.toString(),
                    docxPath.toString())
                    .redirectErrorStream(true)
                    .start();

            boolean finished = process.waitFor(Duration.ofSeconds(60).toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "DOCX to PDF conversion timed out.");
            }
            if (process.exitValue() != 0 || !Files.exists(pdfPath)) {
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Cannot convert DOCX to PDF. Install LibreOffice and set SOFFICE_PATH if needed.");
            }
            return new NormalizedInputFile(pdfPath, "application/pdf");
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Cannot start LibreOffice for DOCX conversion. Install LibreOffice or upload PDF.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "DOCX conversion was interrupted.");
        }
    }

    private String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
