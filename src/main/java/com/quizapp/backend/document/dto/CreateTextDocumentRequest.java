package com.quizapp.backend.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTextDocumentRequest(
        // Optional. If blank, a title is derived from the first line of the text.
        @Size(max = 512, message = "Tên tài liệu tối đa 512 ký tự.")
        String title,

        @NotBlank(message = "Nội dung văn bản không được để trống.")
        @Size(max = 100_000, message = "Nội dung quá dài (tối đa 100.000 ký tự).")
        String text
) {
}
