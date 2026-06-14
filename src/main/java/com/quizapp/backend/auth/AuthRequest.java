package com.quizapp.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthRequest {

    public record Register(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 6, max = 100) String password,
            @NotBlank @Size(min = 2, max = 100) String displayName) {}

    public record Login(
            @Email @NotBlank String email,
            @NotBlank String password) {}

    public record TokenResponse(
            String token,
            String displayName,
            String email,
            String userId) {}
}
