package com.quizapp.backend.quiz;

import com.quizapp.backend.common.ApiException;
import com.quizapp.backend.config.AppProperties;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class QuestionGeneratorClient {
    private final WebClient webClient;
    private final AppProperties properties;

    public QuestionGeneratorClient(WebClient.Builder webClientBuilder, AppProperties properties) {
        this.properties = properties;
        this.webClient = webClientBuilder.baseUrl(properties.getGenerator().getBaseUrl()).build();
    }

    public List<GeneratedQuestion> generate(
            List<GeneratorChunk> chunks,
            int quantity,
            Difficulty difficulty,
            int numChoices,
            int numCorrect
    ) {
        Map<String, Object> request = Map.of(
                "chunks", chunks,
                "num_questions", quantity,
                "answer_style", "multiple_choice",
                "difficulty", difficulty.name().toLowerCase(),
                "num_answer_choices", numChoices,
                "num_correct_answers", numCorrect,
                "answer_source", "concepts");

        GeneratorResponse response = webClient.post()
                .uri("/generate")
                .headers(h -> {
                    String key = properties.getGenerator().getApiKey();
                    if (key != null && !key.isBlank()) {
                        h.set("x-api-key", key);
                    }
                    h.set("ngrok-skip-browser-warning", "true");  // bỏ qua trang cảnh báo ngrok
                })
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GeneratorResponse.class)
                .block(properties.getGenerator().getTimeout());

        if (response == null || response.questions() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Question generator returned an empty response.");
        }
        return response.questions();
    }

    public CapacityResult estimateCapacity(List<GeneratorChunk> chunks) {
        Map<String, Object> request = Map.of("chunks", chunks);

        CapacityResult response = webClient.post()
                .uri("/capacity")
                .headers(h -> {
                    String key = properties.getGenerator().getApiKey();
                    if (key != null && !key.isBlank()) {
                        h.set("x-api-key", key);
                    }
                    h.set("ngrok-skip-browser-warning", "true");
                })
                .bodyValue(request)
                .retrieve()
                .bodyToMono(CapacityResult.class)
                .block(properties.getGenerator().getTimeout());

        if (response == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Question generator returned an empty capacity response.");
        }
        return response;
    }

    public record GeneratorResponse(List<GeneratedQuestion> questions) {
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record CapacityResult(int estimatedQuestions, int usableChunks) {
    }

    public record GeneratorChunk(UUID chunkId, String text, List<String> concepts) {
    }

    public record GeneratedQuestion(UUID sourceChunkId, String question, List<GeneratedAnswer> answer, List<String> concepts) {
    }

    public record GeneratedAnswer(String answer, boolean correct) {
    }
}
