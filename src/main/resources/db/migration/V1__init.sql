create table documents (
    id uuid primary key,
    owner_id uuid not null,
    original_filename varchar(512) not null,
    content_type varchar(255) not null,
    stored_path varchar(1024) not null,
    normalized_pdf_path varchar(1024),
    title varchar(512),
    language_code varchar(32),
    status varchar(32) not null,
    failure_reason text,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table processing_jobs (
    id uuid primary key,
    document_id uuid not null references documents(id) on delete cascade,
    stage varchar(64) not null,
    progress_percent integer not null,
    message text,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table document_pages (
    id uuid primary key,
    document_id uuid not null references documents(id) on delete cascade,
    page_number integer not null,
    normalized_markdown text,
    warnings_json text,
    ocr_confidence varchar(32) not null,
    unique(document_id, page_number)
);

create table document_chunks (
    id uuid primary key,
    document_id uuid not null references documents(id) on delete cascade,
    chunk_index integer not null,
    page_start integer not null,
    page_end integer not null,
    word_count integer not null,
    quizability_score double precision not null,
    section_path_json text,
    text text,
    concepts_json text,
    formulas_json text,
    code_blocks_json text,
    unique(document_id, chunk_index)
);

create table quizzes (
    id uuid primary key,
    owner_id uuid not null,
    document_id uuid not null references documents(id) on delete cascade,
    status varchar(32) not null,
    plan_json text,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table quiz_questions (
    id uuid primary key,
    quiz_id uuid not null references quizzes(id) on delete cascade,
    chunk_id uuid not null references document_chunks(id),
    position integer not null,
    difficulty varchar(32) not null,
    choice_mode varchar(32) not null,
    num_correct integer not null,
    question_text text,
    concepts_json text,
    unique(quiz_id, position)
);

create table answer_options (
    id uuid primary key,
    question_id uuid not null references quiz_questions(id) on delete cascade,
    position integer not null,
    answer_text text,
    correct boolean not null,
    unique(question_id, position)
);

create table quiz_attempts (
    id uuid primary key,
    quiz_id uuid not null references quizzes(id) on delete cascade,
    owner_id uuid not null,
    total_questions integer not null,
    correct_questions integer not null,
    score double precision not null,
    submitted_at timestamptz not null
);

create table attempt_answers (
    id uuid primary key,
    attempt_id uuid not null references quiz_attempts(id) on delete cascade,
    question_id uuid not null references quiz_questions(id),
    selected_option_ids_json text,
    correct boolean not null
);

create table user_concept_stats (
    id uuid primary key,
    owner_id uuid not null,
    document_id uuid not null references documents(id) on delete cascade,
    concept varchar(512) not null,
    seen_count integer not null,
    correct_count integer not null,
    wrong_count integer not null,
    mastery_score double precision not null,
    last_seen_at timestamptz,
    next_review_at timestamptz,
    updated_at timestamptz not null,
    unique(owner_id, document_id, concept)
);

create index idx_documents_owner on documents(owner_id);
create index idx_chunks_document_score on document_chunks(document_id, quizability_score desc);
create index idx_quizzes_owner_created on quizzes(owner_id, created_at desc);
create index idx_attempts_owner_submitted on quiz_attempts(owner_id, submitted_at desc);
create index idx_concept_stats_owner_review on user_concept_stats(owner_id, next_review_at asc);
