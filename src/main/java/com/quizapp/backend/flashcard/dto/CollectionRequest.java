package com.quizapp.backend.flashcard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CollectionRequest(
        @NotBlank(message = "Tên bộ thẻ không được để trống.")
        @Size(max = 255, message = "Tên bộ thẻ tối đa 255 ký tự.")
        String name
) {
}
