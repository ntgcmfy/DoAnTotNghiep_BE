package com.quizapp.backend.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDocumentRequest(
        @NotBlank(message = "Tên tài liệu không được để trống.")
        @Size(max = 512, message = "Tên tài liệu tối đa 512 ký tự.")
        String title
) {
}
