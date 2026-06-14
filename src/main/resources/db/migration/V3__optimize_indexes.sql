-- 1. Quiz listing: user xem danh sách quiz nhanh
CREATE INDEX IF NOT EXISTS idx_quizzes_owner_status ON quizzes(owner_id, status, created_at DESC);

-- 2. Question lookup: load quiz detail
CREATE INDEX IF NOT EXISTS idx_quiz_questions_quiz_position ON quiz_questions(quiz_id, position);

-- 3. Chunk retrieval: lấy chunks theo quizability score
CREATE INDEX IF NOT EXISTS idx_chunks_doc_quizability ON document_chunks(document_id, quizability_score DESC);

-- 4. Concept mastery: personalization queries
CREATE INDEX IF NOT EXISTS idx_concept_stats_review ON user_concept_stats(owner_id, next_review_at)
  WHERE mastery_score < 0.8;  -- Partial index: chỉ track concepts yếu
