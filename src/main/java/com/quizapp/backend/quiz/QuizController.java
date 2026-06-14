package com.quizapp.backend.quiz;

import com.quizapp.backend.quiz.dto.GenerateQuizRequest;
import com.quizapp.backend.quiz.dto.QuizCapacityRequest;
import com.quizapp.backend.quiz.dto.QuizCapacityResponse;
import com.quizapp.backend.quiz.dto.QuizResponse;
import com.quizapp.backend.quiz.dto.QuizSummaryResponse;
import com.quizapp.backend.quiz.dto.SubmitQuizRequest;
import com.quizapp.backend.quiz.dto.SubmitQuizResponse;
import com.quizapp.backend.quiz.dto.UpdateQuizRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quizzes")
public class QuizController {
    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @GetMapping
    public List<QuizSummaryResponse> list(@AuthenticationPrincipal UUID userId) {
        return quizService.listQuizzes(userId);
    }

    @PostMapping("/generate")
    public QuizResponse generate(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody GenerateQuizRequest request
    ) {
        return quizService.generate(userId, request);
    }

    @PostMapping("/capacity")
    public QuizCapacityResponse capacity(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody QuizCapacityRequest request
    ) {
        return quizService.estimateCapacity(userId, request.documentId(), request.chunkIds());
    }

    @GetMapping("/{quizId}")
    public QuizResponse getQuiz(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID quizId
    ) {
        return quizService.getQuiz(userId, quizId, false);
    }

    @PostMapping("/{quizId}/submit")
    public SubmitQuizResponse submit(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID quizId,
            @Valid @RequestBody SubmitQuizRequest request
    ) {
        return quizService.submit(userId, quizId, request);
    }

    @PostMapping("/{quizId}/adaptive")
    public QuizResponse generateAdaptive(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID quizId
    ) {
        return quizService.generateAdaptive(userId, quizId);
    }

    @PatchMapping("/{quizId}")
    public QuizSummaryResponse update(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID quizId,
            @Valid @RequestBody UpdateQuizRequest request
    ) {
        return quizService.setSaved(userId, quizId, request.saved());
    }

    @DeleteMapping("/{quizId}")
    public void delete(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID quizId
    ) {
        quizService.deleteQuiz(userId, quizId);
    }
}
