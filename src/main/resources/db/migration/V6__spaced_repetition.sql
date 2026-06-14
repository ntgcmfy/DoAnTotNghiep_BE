-- Spaced-repetition scheduling state for the SM-2 algorithm (Wozniak, 1990).
-- Replaces the previous fixed +3 days / +1 hour review schedule with a
-- history-dependent interval that expands as a concept is recalled correctly.
ALTER TABLE user_concept_stats
    ADD COLUMN ease_factor   double precision NOT NULL DEFAULT 2.5,
    ADD COLUMN repetition    integer          NOT NULL DEFAULT 0,
    ADD COLUMN interval_days integer          NOT NULL DEFAULT 0;
