package com.quizapp.backend.auth;

import com.quizapp.backend.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthRequest.TokenResponse register(AuthRequest.Register req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already in use.");
        }
        var user = new UserEntity(
                req.email(),
                passwordEncoder.encode(req.password()),
                req.displayName());
        userRepository.save(user);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getDisplayName());
        return new AuthRequest.TokenResponse(token, user.getDisplayName(), user.getEmail(), user.getId().toString());
    }

    public AuthRequest.TokenResponse login(AuthRequest.Login req) {
        var user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password."));
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }
        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getDisplayName());
        return new AuthRequest.TokenResponse(token, user.getDisplayName(), user.getEmail(), user.getId().toString());
    }
}
