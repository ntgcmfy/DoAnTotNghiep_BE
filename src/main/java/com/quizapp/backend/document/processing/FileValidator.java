package com.quizapp.backend.document.processing;

import com.quizapp.backend.common.ApiException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Kiểm tra file NGAY khi upload — TRƯỚC khi lưu + đưa cho Gemini.
 * Tránh tốn tài nguyên xử lý file lỗi / quá lớn / nhiều trang.
 *
 *  1. Magic bytes  — xác thực định dạng THẬT (chống đổi đuôi giả).
 *  2. Dung lượng   — giới hạn riêng theo loại (PDF/DOCX ≤ 20MB khớp Gemini, ảnh ≤ 10MB).
 *  3. Số trang PDF — chặn PDF quá dài (tốn Gemini, dễ timeout).
 */
@Component
public class FileValidator {

    private static final long MAX_DOCX_BYTES  = 20L * 1024 * 1024;   // DOCX — khớp Gemini inline
    private static final long MAX_PDF_BYTES    = 50L * 1024 * 1024;   // PDF — cho phép lớn, sẽ trích trang
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;   // PNG/JPG/WEBP
    private static final int  MAX_PDF_PAGES   = 50;                  // tối đa số trang xử lý 1 lần

    /**
     * @param pageStart 1-based, có thể null (xử lý cả file)
     * @param pageEnd   1-based (bao gồm), có thể null
     */
    public void validate(MultipartFile file, String filename, Integer pageStart, Integer pageEnd) {
        long size = file.getSize();
        if (size <= 0) {
            throw bad("File rỗng hoặc không đọc được.");
        }
        String ext = extension(filename);

        byte[] head;
        try {
            head = file.getInputStream().readNBytes(16);
        } catch (IOException e) {
            throw bad("Không đọc được nội dung file.");
        }

        // 1) Magic bytes phải khớp đuôi file
        if (!magicMatches(ext, head)) {
            throw bad("Nội dung file không khớp định dạng '" + ext + "'. File có thể bị hỏng hoặc đổi đuôi.");
        }

        // 2) Dung lượng theo loại
        boolean isImage = ext.equals(".png") || ext.equals(".jpg") || ext.equals(".jpeg") || ext.equals(".webp");
        boolean isPdf = ext.equals(".pdf");
        long limit = isImage ? MAX_IMAGE_BYTES : (isPdf ? MAX_PDF_BYTES : MAX_DOCX_BYTES);
        if (size > limit) {
            throw bad(String.format(Locale.ROOT,
                    "File quá lớn (%.1f MB). Tối đa %.0f MB cho loại %s.",
                    size / 1024.0 / 1024.0, limit / 1024.0 / 1024.0,
                    isImage ? "ảnh" : (isPdf ? "PDF" : "DOCX")));
        }

        // 3) PDF: kiểm tra số trang / khoảng trang
        if (isPdf) {
            int pages = countPdfPages(file);
            if (pageStart != null && pageEnd != null) {
                // Người dùng đã chọn khoảng trang → validate khoảng đó
                if (pageStart < 1 || pageEnd > pages || pageStart > pageEnd) {
                    throw bad(String.format(Locale.ROOT,
                            "Khoảng trang không hợp lệ (%d–%d). File có %d trang.", pageStart, pageEnd, pages));
                }
                int selected = pageEnd - pageStart + 1;
                if (selected > MAX_PDF_PAGES) {
                    throw bad(String.format(Locale.ROOT,
                            "Bạn chọn %d trang, tối đa %d trang mỗi lần. Hãy chọn khoảng nhỏ hơn.",
                            selected, MAX_PDF_PAGES));
                }
            } else if (pages > MAX_PDF_PAGES) {
                // PDF dài mà không chọn khoảng → yêu cầu chọn
                throw bad(String.format(Locale.ROOT,
                        "PDF có %d trang, vượt %d trang. Vui lòng chọn khoảng trang cần xử lý.",
                        pages, MAX_PDF_PAGES));
            }
        }
    }

    /** Trích các trang [start, end] (1-based, bao gồm) thành PDF mới. */
    public byte[] extractPdfPages(MultipartFile file, int pageStart, int pageEnd) {
        try (PDDocument src = Loader.loadPDF(file.getBytes());
             PDDocument out = new PDDocument()) {
            int total = src.getNumberOfPages();
            int from = Math.max(1, pageStart);
            int to = Math.min(total, pageEnd);
            for (int i = from; i <= to; i++) {
                PDPage page = src.getPage(i - 1);   // PDFBox 0-based
                out.importPage(page);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            out.save(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw bad("Không trích được trang PDF đã chọn.");
        }
    }

    /** Đếm số trang PDF (cho frontend hiển thị / validate). */
    public int pdfPageCount(MultipartFile file) {
        return countPdfPages(file);
    }

    private boolean magicMatches(String ext, byte[] h) {
        if (h.length < 4) {
            return false;
        }
        switch (ext) {
            case ".pdf":
                return h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F';
            case ".png":
                return (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
            case ".jpg":
            case ".jpeg":
                return (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
            case ".webp":
                return h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                        && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
            case ".docx":
                // DOCX = ZIP container → "PK\x03\x04"
                return h[0] == 'P' && h[1] == 'K' && h[2] == 0x03 && h[3] == 0x04;
            default:
                return false;
        }
    }

    private int countPdfPages(MultipartFile file) {
        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            return doc.getNumberOfPages();
        } catch (Exception e) {
            throw bad("PDF bị lỗi, không đọc được nội dung.");
        }
    }

    private String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase(Locale.ROOT) : "";
    }

    private ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
