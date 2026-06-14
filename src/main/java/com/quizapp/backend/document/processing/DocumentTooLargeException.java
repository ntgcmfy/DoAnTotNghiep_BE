package com.quizapp.backend.document.processing;

import com.quizapp.backend.common.ApiException;
import org.springframework.http.HttpStatus;

public class DocumentTooLargeException extends ApiException {
    public DocumentTooLargeException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
