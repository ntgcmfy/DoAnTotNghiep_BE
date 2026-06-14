package com.quizapp.backend.document.processing;

import com.quizapp.backend.gemini.NormalizedDocument;
import com.quizapp.backend.gemini.NormalizedDocument.NormalizedChunk;
import com.quizapp.backend.gemini.NormalizedDocument.NormalizedPage;
import com.quizapp.backend.gemini.NormalizedDocument.ExtractionQuality;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class DocxLocalParser implements LocalDocumentParser {

    private static final int MAX_WORDS = 9000; // ~ 30 pages

    @Override
    public boolean supports(String mimeType, String filename) {
        return filename != null && filename.toLowerCase().endsWith(".docx");
    }

    @Override
    public NormalizedDocument parse(Path filePath) throws Exception {
        try (FileInputStream fis = new FileInputStream(filePath.toFile());
             XWPFDocument document = new XWPFDocument(fis)) {
            
            List<XWPFPictureData> pictures = document.getAllPictures();
            if (pictures.size() > 50) {
                // Quá nhiều ảnh, văn bản có thể phức tạp -> chuyển Gemini xử lý
                throw new ComplexDocumentException("DOCX chứa quá nhiều hình ảnh (" + pictures.size() + ").");
            }

            List<NormalizedChunk> chunks = new ArrayList<>();
            List<String> currentSectionPath = new ArrayList<>();
            currentSectionPath.add("Document");
            
            int chunkIndex = 0;
            StringBuilder currentChunkText = new StringBuilder();
            int totalWordCount = 0;

            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText().trim();
                    if (text.isEmpty()) continue;

                    String style = paragraph.getStyle();
                    // Nhận diện Heading (Heading1, Heading2, v.v. hoặc Tiêu đề 1)
                    if (style != null && style.toLowerCase().contains("heading")) {
                        // Lưu chunk cũ lại
                        if (currentChunkText.length() > 0) {
                            chunks.add(buildChunk(chunkIndex++, currentSectionPath, currentChunkText.toString().trim()));
                            currentChunkText.setLength(0);
                        }
                        
                        // Cập nhật section path
                        try {
                            int level = Integer.parseInt(style.replaceAll("[^0-9]", ""));
                            if (level > 0 && level <= 6) {
                                while (currentSectionPath.size() > level) {
                                    currentSectionPath.remove(currentSectionPath.size() - 1);
                                }
                                if (currentSectionPath.size() == level) {
                                    currentSectionPath.remove(currentSectionPath.size() - 1);
                                }
                                currentSectionPath.add(text);
                            }
                        } catch (NumberFormatException e) {
                            // Fallback
                            currentSectionPath.clear();
                            currentSectionPath.add(text);
                        }
                    } else {
                        currentChunkText.append(text).append("\n\n");
                        totalWordCount += text.split("\\s+").length;
                        
                        if (totalWordCount > MAX_WORDS) {
                            throw new DocumentTooLargeException("Tài liệu quá lớn (ước tính > 30 trang). Vui lòng cắt nhỏ tài liệu.");
                        }

                        // Nếu đoạn văn bản quá dài mà không có heading, tự động tách
                        if (currentChunkText.toString().split("\\s+").length >= 300) {
                            chunks.add(buildChunk(chunkIndex++, currentSectionPath, currentChunkText.toString().trim()));
                            currentChunkText.setLength(0);
                        }
                    }
                }
            }

            // Xử lý chunk cuối cùng
            if (currentChunkText.length() > 0) {
                chunks.add(buildChunk(chunkIndex++, currentSectionPath, currentChunkText.toString().trim()));
            }

            if (totalWordCount < 50) {
                throw new ComplexDocumentException("DOCX chứa quá ít chữ.");
            }

            // DOCX không có khái niệm trang vật lý cố định
            List<NormalizedPage> pages = List.of(
                new NormalizedPage(1, "Text extracted logically. No physical pagination available.", List.of(), "high")
            );

            ExtractionQuality quality = new ExtractionQuality("high", "simple");

            return new NormalizedDocument(
                filePath.toFile().getName(),
                "en", 
                pages,
                chunks,
                quality,
                List.of()
            );
        }
    }

    private NormalizedChunk buildChunk(int index, List<String> sectionPath, String text) {
        int wordCount = text.split("\\s+").length;
        double score = wordCount > 30 ? 0.8 : 0.3;
        
        return new NormalizedChunk(
            index,
            new ArrayList<>(sectionPath),
            1, 
            1,
            text,
            List.of(), 
            List.of(),
            List.of(),
            score
        );
    }
}
