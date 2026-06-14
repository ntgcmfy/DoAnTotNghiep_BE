package com.quizapp.backend.document.processing;

import com.quizapp.backend.gemini.NormalizedDocument;
import com.quizapp.backend.gemini.NormalizedDocument.NormalizedChunk;
import com.quizapp.backend.gemini.NormalizedDocument.NormalizedPage;
import com.quizapp.backend.gemini.NormalizedDocument.ExtractionQuality;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class PdfLocalParser implements LocalDocumentParser {

    private static final int MAX_PAGES = 30;

    @Override
    public boolean supports(String mimeType, String filename) {
        return filename != null && filename.toLowerCase().endsWith(".pdf");
    }

    @Override
    public NormalizedDocument parse(Path filePath) throws Exception {
        File file = filePath.toFile();
        try (PDDocument document = Loader.loadPDF(file)) {
            int numPages = document.getNumberOfPages();
            
            if (numPages > MAX_PAGES) {
                throw new DocumentTooLargeException("Tài liệu quá lớn (" + numPages + " trang). Hệ thống chỉ hỗ trợ tối đa " + MAX_PAGES + " trang.");
            }
            if (numPages == 0) {
                throw new ComplexDocumentException("PDF rỗng hoặc không có trang nào.");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            // Xóa header/footer đơn giản bằng cách bỏ qua text ở rìa, nhưng PDFTextStripper không có sẵn. 
            // Ta cứ lấy toàn bộ text.
            stripper.setSortByPosition(true);

            String fullText = stripper.getText(document);
            if (fullText == null || fullText.trim().length() < numPages * 20) {
                // Rất ít chữ => Khả năng cao là PDF scan (ảnh)
                throw new ComplexDocumentException("PDF chứa quá ít văn bản (có thể là file scan hoặc chứa toàn ảnh).");
            }

            // Chunking đơn giản: tách theo đoạn văn (khoảng trắng lớn hoặc xuống dòng kép)
            String[] paragraphs = fullText.split("(?m)^\\s*$"); // Split by empty lines
            
            List<NormalizedChunk> chunks = new ArrayList<>();
            int chunkIndex = 0;
            StringBuilder currentChunkText = new StringBuilder();
            
            for (String p : paragraphs) {
                String cleanP = p.trim();
                if (cleanP.isEmpty()) continue;
                
                currentChunkText.append(cleanP).append("\n\n");
                
                // Gộp khoảng 150-300 từ thành 1 chunk
                if (currentChunkText.toString().split("\\s+").length >= 150) {
                    chunks.add(buildChunk(chunkIndex++, currentChunkText.toString().trim()));
                    currentChunkText.setLength(0);
                }
            }
            if (currentChunkText.length() > 0) {
                chunks.add(buildChunk(chunkIndex++, currentChunkText.toString().trim()));
            }

            // Tạo danh sách trang (chỉ mang tính tượng trưng vì đã lấy full text)
            List<NormalizedPage> pages = new ArrayList<>();
            for (int i = 1; i <= numPages; i++) {
                // Để đơn giản, không extract riêng từng trang mà coi như đã lấy hết.
                // Thực tế có thể extract riêng bằng stripper.setStartPage(i).
                pages.add(new NormalizedPage(i, "Page " + i + " content extracted in chunks.", List.of(), "high"));
            }

            ExtractionQuality quality = new ExtractionQuality("high", "simple");

            return new NormalizedDocument(
                file.getName(),
                "en", // Hoặc auto-detect
                pages,
                chunks,
                quality,
                List.of()
            );
        }
    }

    private NormalizedChunk buildChunk(int index, String text) {
        int wordCount = text.split("\\s+").length;
        // Điểm quizability heuristic: chữ càng nhiều điểm càng cao (tối đa 0.8), quá ít chữ thì 0.3
        double score = wordCount > 30 ? 0.8 : 0.3;
        
        return new NormalizedChunk(
            index,
            List.of("Document Section"), // PDF khó trích xuất heading, gán mặc định
            1, // Không map chính xác số trang được nếu gộp text, tạm để 1
            1,
            text,
            List.of(), // Key concepts (Gemini sẽ làm tốt hơn, ở đây để rỗng)
            List.of(),
            List.of(),
            score
        );
    }
}
