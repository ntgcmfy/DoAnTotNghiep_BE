-- V2: Fix foreign key constraints that would cause errors on cascading deletes

-- 1. quiz_questions.chunk_id: allow NULL when the source chunk is deleted
--    (the question text is already stored independently)
ALTER TABLE quiz_questions ALTER COLUMN chunk_id DROP NOT NULL;
ALTER TABLE quiz_questions DROP CONSTRAINT IF EXISTS quiz_questions_chunk_id_fkey;
ALTER TABLE quiz_questions
    ADD CONSTRAINT quiz_questions_chunk_id_fkey
    FOREIGN KEY (chunk_id) REFERENCES document_chunks(id) ON DELETE SET NULL;

-- 2. attempt_answers.question_id: cascade delete when the question is deleted
ALTER TABLE attempt_answers DROP CONSTRAINT IF EXISTS attempt_answers_question_id_fkey;
ALTER TABLE attempt_answers
    ADD CONSTRAINT attempt_answers_question_id_fkey
    FOREIGN KEY (question_id) REFERENCES quiz_questions(id) ON DELETE CASCADE;

-- 3. Add missing index for quiz_questions lookup by quiz_id
CREATE INDEX IF NOT EXISTS idx_quiz_questions_quiz ON quiz_questions(quiz_id);

-- 4. Add missing index for answer_options lookup by question_id
CREATE INDEX IF NOT EXISTS idx_answer_options_question ON answer_options(question_id);

-- 5. Add missing index for attempt_answers lookup by attempt_id
CREATE INDEX IF NOT EXISTS idx_attempt_answers_attempt ON attempt_answers(attempt_id);
