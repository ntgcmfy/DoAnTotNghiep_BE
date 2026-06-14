package com.quizapp.backend.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthRequest.TokenResponse register(@Valid @RequestBody AuthRequest.Register request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthRequest.TokenResponse login(@Valid @RequestBody AuthRequest.Login request) {
        return authService.login(request);
    }
}
