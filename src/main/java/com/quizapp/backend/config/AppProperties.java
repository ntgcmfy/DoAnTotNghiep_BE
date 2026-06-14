package com.quizapp.backend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private final Storage storage = new Storage();
    private final Gemini gemini = new Gemini();
    private final Generator generator = new Generator();

    public Storage getStorage() {
        return storage;
    }

    public Gemini getGemini() {
        return gemini;
    }

    public Generator getGenerator() {
        return generator;
    }

    public static class Storage {
        private String root = "./data/uploads";

        public String getRoot() {
            return root;
        }

        public void setRoot(String root) {
            this.root = root;
        }
    }

    public static class Gemini {
        private String apiKey;
        private String model = "gemini-2.5-flash";
        private String generateUrl;
        private long inlineMaxBytes = 20L * 1024L * 1024L;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getGenerateUrl() {
            return generateUrl;
        }

        public void setGenerateUrl(String generateUrl) {
            this.generateUrl = generateUrl;
        }

        public long getInlineMaxBytes() {
            return inlineMaxBytes;
        }

        public void setInlineMaxBytes(long inlineMaxBytes) {
            this.inlineMaxBytes = inlineMaxBytes;
        }
    }

    public static class Generator {
        private String baseUrl = "http://localhost:8001";
        private String apiKey = "";
        private long timeoutSeconds = 120;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public Duration getTimeout() {
            return Duration.ofSeconds(timeoutSeconds);
        }

        public long getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(long timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
