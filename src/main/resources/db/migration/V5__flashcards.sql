-- V5: Flashcards + "saved" flag on quizzes (for 7-day auto-cleanup of unsaved content)

-- Quizzes the user has not explicitly saved are eligible for cleanup.
ALTER TABLE quizzes ADD COLUMN saved boolean NOT NULL DEFAULT false;

CREATE TABLE flashcard_collections (
    id         uuid PRIMARY KEY,
    owner_id   uuid NOT NULL,
    name       varchar(255) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE flashcards (
    id                 uuid PRIMARY KEY,
    owner_id           uuid NOT NULL,
    collection_id      uuid REFERENCES flashcard_collections(id) ON DELETE CASCADE,
    front              text NOT NULL,
    back               text NOT NULL,
    source_question_id uuid,
    source_document_id uuid,
    created_at         timestamptz NOT NULL
);

CREATE INDEX idx_flashcard_collections_owner ON flashcard_collections(owner_id, created_at DESC);
CREATE INDEX idx_flashcards_owner ON flashcards(owner_id);
CREATE INDEX idx_flashcards_collection ON flashcards(collection_id);
-- Loose (uncollected) cards are the ones the cleanup job sweeps.
CREATE INDEX idx_flashcards_loose_created ON flashcards(created_at) WHERE collection_id IS NULL;
CREATE INDEX idx_quizzes_saved_created ON quizzes(saved, created_at);
