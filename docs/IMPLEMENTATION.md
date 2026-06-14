# Quiz Backend Implementation

This backend is designed for the real flow of the product:

```text
User uploads PDF/DOCX/image
-> Spring stores file and creates processing job
-> DOCX is converted to PDF with LibreOffice when needed
-> Gemini normalizes document into pages and topic chunks
-> chunks are stored in PostgreSQL
-> user selects topics and quiz plan
-> Spring calls Python question-generator service
-> answers are submitted
-> concept mastery is updated for personalization
```

## Runtime Components

- `quiz_backend`: Spring Boot REST API.
- PostgreSQL: metadata, normalized chunks, quizzes, attempts, mastery stats.
- `question_generator/api_service.py`: Python model service used by Spring.
- Gemini API: document understanding, OCR/layout normalization, topic chunking.
- LibreOffice: DOCX to PDF conversion for DOCX files that contain images, formulas, layout, or tables.

## Local Startup

Start PostgreSQL:

```powershell
cd D:\DoAnTotNghiep\quiz_backend
docker compose up -d
```

Start Python question generator:

```powershell
cd D:\DoAnTotNghiep
.\.venv\Scripts\pip.exe install -r question_generator\requirements.txt
.\.venv\Scripts\python.exe -m uvicorn question_generator.api_service:app --host 127.0.0.1 --port 8001
```

Start Spring backend:

```powershell
cd D:\DoAnTotNghiep\quiz_backend
$env:GEMINI_API_KEY="your_key"
.\mvnw.cmd spring-boot:run
```

If DOCX conversion is needed:

```powershell
$env:SOFFICE_PATH="C:\Program Files\LibreOffice\program\soffice.exe"
```

## REST Flow

Use a real authenticated user id later. For the MVP, APIs accept `X-User-Id` as UUID.

Upload:

```http
POST /api/documents/upload
X-User-Id: 11111111-1111-1111-1111-111111111111
Content-Type: multipart/form-data

file=<pdf|docx|png|jpg|jpeg|webp>
```

Poll processing status:

```http
GET /api/documents/{documentId}/status
X-User-Id: 11111111-1111-1111-1111-111111111111
```

Get generated chunks/topics:

```http
GET /api/documents/{documentId}/chunks
X-User-Id: 11111111-1111-1111-1111-111111111111
```

Generate a mixed quiz plan:

```http
POST /api/quizzes/generate
X-User-Id: 11111111-1111-1111-1111-111111111111
Content-Type: application/json

{
  "documentId": "document_uuid",
  "quizPlan": [
    {
      "quantity": 2,
      "difficulty": "EASY",
      "questionType": "MULTIPLE_CHOICE",
      "choiceMode": "MULTIPLE_CORRECT",
      "numChoices": 5,
      "numCorrect": 2,
      "chunkIds": []
    },
    {
      "quantity": 3,
      "difficulty": "HARD",
      "questionType": "MULTIPLE_CHOICE",
      "choiceMode": "SINGLE_CORRECT",
      "numChoices": 4,
      "numCorrect": 1,
      "chunkIds": []
    }
  ],
  "personalization": {
    "enabled": true,
    "focusWrongConcepts": true,
    "avoidRecentQuestions": true
  }
}
```

Submit:

```http
POST /api/quizzes/{quizId}/submit
X-User-Id: 11111111-1111-1111-1111-111111111111
Content-Type: application/json

{
  "answers": [
    {
      "questionId": "question_uuid",
      "selectedOptionIds": ["option_uuid"]
    }
  ]
}
```

History:

```http
GET /api/users/me/history
X-User-Id: 11111111-1111-1111-1111-111111111111
```

## UI Controls That Match The Backend

Simple mode:

- Document/topic selector.
- Total questions.
- Difficulty: easy, medium, hard, mixed.
- Answer mode: one correct, multiple correct, mixed.
- Number of choices: 3, 4, 5.
- Personalization toggle.
- Retry wrong questions toggle.

Advanced mode:

| Quantity | Difficulty | Choice mode | Choices | Correct answers | Topics |
| --- | --- | --- | --- | --- | --- |
| 2 | Easy | Multiple correct | 5 | 2 | selected chunks |
| 3 | Hard | Single correct | 4 | 1 | selected chunks |

Validation:

- `numCorrect <= numChoices`
- `SINGLE_CORRECT` requires `numCorrect = 1`
- `MULTIPLE_CORRECT` requires `numCorrect >= 2`
- Total quiz size should stay under 100 questions
- If a selected topic has low `quizabilityScore`, warn the user before generating

## Why Gemini Is Only In Preprocessing

Gemini is used for OCR/layout/document understanding, not for final quiz state management. The backend stores the normalized chunks so repeated quiz generation is deterministic and auditable. This also prevents paying Gemini again every time the user wants another quiz from the same document.

## Production Gaps To Close Next

- Replace `X-User-Id` with JWT/OAuth2 resource server.
- Use Gemini Files API for files larger than `app.gemini.inline-max-bytes`.
- Move document processing jobs to a durable queue if multiple workers are needed.
- Store large raw files in S3/MinIO instead of local disk.
- Add rate limits and per-user storage quotas.
