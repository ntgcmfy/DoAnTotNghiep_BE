# Quiz Backend

Spring Boot backend for document normalization, quiz generation, attempts, and personalized study history.

Important files:

- `src/main/resources/db/migration/V1__init.sql`: PostgreSQL schema.
- `src/main/java/com/quizapp/backend/document`: upload, processing jobs, normalized chunks.
- `src/main/java/com/quizapp/backend/gemini`: Gemini normalization client and strict JSON schema.
- `src/main/java/com/quizapp/backend/quiz`: quiz generation, submit, scoring, personalization.
- `src/main/java/com/quizapp/backend/user`: study history and concept mastery.
- `docs/IMPLEMENTATION.md`: runtime setup and API flow.

Build:

```powershell
.\mvnw.cmd -DskipTests package
```

Run:

```powershell
docker compose up -d
$env:GEMINI_API_KEY="your_key"
.\mvnw.cmd spring-boot:run
```
