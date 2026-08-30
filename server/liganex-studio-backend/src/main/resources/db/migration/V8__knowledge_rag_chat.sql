-- Knowledge RAG and chat persistence.
-- PostgreSQL is the source of truth; pgvector is the first index backend.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE knowledge_base (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(1000),
    status          VARCHAR(24)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_knowledge_base_status CHECK (status IN ('ACTIVE', 'DELETING')),
    CONSTRAINT ux_knowledge_base_id_owner UNIQUE (id, owner_user_id)
);

CREATE TABLE knowledge_document (
    id                  BIGSERIAL PRIMARY KEY,
    owner_user_id       BIGINT        NOT NULL,
    knowledge_base_id   BIGINT        NOT NULL,
    title               VARCHAR(255)  NOT NULL,
    source_type         VARCHAR(16)   NOT NULL,
    media_type          VARCHAR(128),
    original_filename   VARCHAR(255),
    size_bytes          BIGINT        NOT NULL,
    content_sha256      CHAR(64)      NOT NULL,
    extracted_text      TEXT,
    status              VARCHAR(24)   NOT NULL DEFAULT 'PENDING',
    progress            SMALLINT      NOT NULL DEFAULT 0,
    chunk_count         INTEGER       NOT NULL DEFAULT 0,
    index_version       VARCHAR(64),
    error_summary       VARCHAR(1000),
    indexed_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_knowledge_document_base_owner
        FOREIGN KEY (knowledge_base_id, owner_user_id)
        REFERENCES knowledge_base (id, owner_user_id) ON DELETE CASCADE,
    CONSTRAINT ck_knowledge_document_source_type
        CHECK (source_type IN ('TEXT', 'FILE')),
    CONSTRAINT ck_knowledge_document_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED', 'DELETING')),
    CONSTRAINT ck_knowledge_document_progress CHECK (progress BETWEEN 0 AND 100),
    CONSTRAINT ck_knowledge_document_size CHECK (size_bytes >= 0),
    CONSTRAINT ck_knowledge_document_chunk_count CHECK (chunk_count >= 0),
    CONSTRAINT ux_knowledge_document_id_scope
        UNIQUE (id, owner_user_id, knowledge_base_id),
    CONSTRAINT ux_knowledge_document_content
        UNIQUE (owner_user_id, knowledge_base_id, content_sha256)
);

CREATE TABLE knowledge_document_blob (
    document_id        BIGINT       PRIMARY KEY,
    owner_user_id      BIGINT       NOT NULL,
    knowledge_base_id  BIGINT       NOT NULL,
    content            BYTEA        NOT NULL,
    size_bytes         BIGINT       NOT NULL,
    content_sha256     CHAR(64)     NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_knowledge_blob_document_scope
        FOREIGN KEY (document_id, owner_user_id, knowledge_base_id)
        REFERENCES knowledge_document (id, owner_user_id, knowledge_base_id) ON DELETE CASCADE,
    CONSTRAINT ck_knowledge_blob_size CHECK (size_bytes >= 0),
    CONSTRAINT ck_knowledge_blob_size_matches CHECK (octet_length(content) = size_bytes)
);

CREATE TABLE knowledge_chunk (
    id                  BIGSERIAL PRIMARY KEY,
    chunk_id            VARCHAR(128) NOT NULL,
    owner_user_id       BIGINT       NOT NULL,
    knowledge_base_id   BIGINT       NOT NULL,
    document_id         BIGINT       NOT NULL,
    parent_chunk_id     VARCHAR(128),
    chunk_type          VARCHAR(16)  NOT NULL,
    ordinal             INTEGER      NOT NULL,
    index_version       VARCHAR(64)  NOT NULL,
    content             TEXT         NOT NULL,
    parent_content      TEXT,
    lexical_terms       TEXT         NOT NULL DEFAULT '',
    search_vector       TSVECTOR GENERATED ALWAYS AS
                            (to_tsvector('simple', lexical_terms)) STORED,
    embedding           VECTOR(1536),
    source_name         VARCHAR(255) NOT NULL,
    status              VARCHAR(24)  NOT NULL DEFAULT 'READY',
    page_number         INTEGER,
    start_offset        INTEGER,
    end_offset          INTEGER,
    metadata            JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_knowledge_chunk_document_scope
        FOREIGN KEY (document_id, owner_user_id, knowledge_base_id)
        REFERENCES knowledge_document (id, owner_user_id, knowledge_base_id) ON DELETE CASCADE,
    CONSTRAINT ck_knowledge_chunk_type CHECK (chunk_type IN ('PARENT', 'CHILD')),
    CONSTRAINT ck_knowledge_chunk_status CHECK (status IN ('READY', 'DELETING')),
    CONSTRAINT ck_knowledge_chunk_ordinal CHECK (ordinal >= 0),
    CONSTRAINT ck_knowledge_chunk_page CHECK (page_number IS NULL OR page_number > 0),
    CONSTRAINT ck_knowledge_chunk_offsets CHECK (
        (start_offset IS NULL AND end_offset IS NULL)
        OR (start_offset >= 0 AND end_offset >= start_offset)
    ),
    CONSTRAINT ux_knowledge_chunk_stable_key
        UNIQUE (chunk_id, owner_user_id, knowledge_base_id, document_id, index_version),
    CONSTRAINT fk_knowledge_chunk_parent_scope
        FOREIGN KEY (parent_chunk_id, owner_user_id, knowledge_base_id, document_id, index_version)
        REFERENCES knowledge_chunk (chunk_id, owner_user_id, knowledge_base_id, document_id, index_version)
        ON DELETE CASCADE
);

CREATE TABLE knowledge_index_job (
    id                  BIGSERIAL PRIMARY KEY,
    owner_user_id       BIGINT        NOT NULL,
    knowledge_base_id   BIGINT        NOT NULL,
    document_id         BIGINT        NOT NULL,
    job_type            VARCHAR(24)   NOT NULL DEFAULT 'INDEX',
    idempotency_key     VARCHAR(255)  NOT NULL,
    status              VARCHAR(24)   NOT NULL DEFAULT 'PENDING',
    progress            SMALLINT      NOT NULL DEFAULT 0,
    retry_count         INTEGER       NOT NULL DEFAULT 0,
    max_retries         INTEGER       NOT NULL DEFAULT 4,
    next_retry_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    locked_by           VARCHAR(128),
    locked_at           TIMESTAMPTZ,
    error_summary       VARCHAR(1000),
    payload             JSONB         NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ,
    CONSTRAINT fk_knowledge_job_document_scope
        FOREIGN KEY (document_id, owner_user_id, knowledge_base_id)
        REFERENCES knowledge_document (id, owner_user_id, knowledge_base_id) ON DELETE CASCADE,
    CONSTRAINT ck_knowledge_job_type
        CHECK (job_type IN ('INDEX', 'REINDEX', 'DELETE')),
    CONSTRAINT ck_knowledge_job_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_knowledge_job_progress CHECK (progress BETWEEN 0 AND 100),
    CONSTRAINT ck_knowledge_job_retries
        CHECK (retry_count >= 0 AND max_retries >= 0),
    CONSTRAINT ux_knowledge_job_idempotency UNIQUE (owner_user_id, idempotency_key)
);

CREATE TABLE knowledge_document_index (
    id                  BIGSERIAL PRIMARY KEY,
    owner_user_id       BIGINT        NOT NULL,
    knowledge_base_id   BIGINT        NOT NULL,
    document_id         BIGINT        NOT NULL,
    backend             VARCHAR(32)   NOT NULL,
    embedding_model     VARCHAR(255)  NOT NULL,
    dimensions          INTEGER       NOT NULL,
    index_version       VARCHAR(64)   NOT NULL,
    status              VARCHAR(24)   NOT NULL DEFAULT 'BUILDING',
    chunk_count         INTEGER       NOT NULL DEFAULT 0,
    error_summary       VARCHAR(1000),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    ready_at            TIMESTAMPTZ,
    CONSTRAINT fk_knowledge_document_index_scope
        FOREIGN KEY (document_id, owner_user_id, knowledge_base_id)
        REFERENCES knowledge_document (id, owner_user_id, knowledge_base_id) ON DELETE CASCADE,
    CONSTRAINT ck_knowledge_document_index_backend
        CHECK (backend IN ('PGVECTOR', 'QDRANT', 'REDIS')),
    CONSTRAINT ck_knowledge_document_index_status
        CHECK (status IN ('BUILDING', 'READY', 'FAILED', 'DELETING')),
    CONSTRAINT ck_knowledge_document_index_dimensions CHECK (dimensions > 0),
    CONSTRAINT ck_knowledge_document_index_chunk_count CHECK (chunk_count >= 0),
    CONSTRAINT ux_knowledge_document_index_version
        UNIQUE (owner_user_id, document_id, backend, embedding_model, index_version)
);

CREATE TABLE chat_conversation (
    id                    BIGSERIAL PRIMARY KEY,
    owner_user_id         BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    title                 VARCHAR(255) NOT NULL,
    status                VARCHAR(24)  NOT NULL DEFAULT 'ACTIVE',
    next_message_sequence BIGINT       NOT NULL DEFAULT 1,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_chat_conversation_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_chat_conversation_sequence CHECK (next_message_sequence > 0),
    CONSTRAINT ux_chat_conversation_id_owner UNIQUE (id, owner_user_id)
);

CREATE TABLE chat_conversation_kb (
    conversation_id    BIGINT      NOT NULL,
    knowledge_base_id  BIGINT      NOT NULL,
    owner_user_id      BIGINT      NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (conversation_id, knowledge_base_id),
    CONSTRAINT fk_chat_conversation_kb_conversation_owner
        FOREIGN KEY (conversation_id, owner_user_id)
        REFERENCES chat_conversation (id, owner_user_id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_conversation_kb_base_owner
        FOREIGN KEY (knowledge_base_id, owner_user_id)
        REFERENCES knowledge_base (id, owner_user_id) ON DELETE CASCADE
);

CREATE TABLE chat_message (
    id                BIGSERIAL PRIMARY KEY,
    owner_user_id     BIGINT       NOT NULL,
    conversation_id  BIGINT       NOT NULL,
    sequence          BIGINT       NOT NULL,
    role              VARCHAR(16)  NOT NULL,
    content           TEXT         NOT NULL,
    status            VARCHAR(24)  NOT NULL DEFAULT 'COMPLETED',
    citations         JSONB        NOT NULL DEFAULT '[]'::jsonb,
    token_count       INTEGER,
    error_summary     VARCHAR(1000),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at      TIMESTAMPTZ,
    CONSTRAINT fk_chat_message_conversation_owner
        FOREIGN KEY (conversation_id, owner_user_id)
        REFERENCES chat_conversation (id, owner_user_id) ON DELETE CASCADE,
    CONSTRAINT ck_chat_message_sequence CHECK (sequence > 0),
    CONSTRAINT ck_chat_message_role CHECK (role IN ('SYSTEM', 'USER', 'ASSISTANT')),
    CONSTRAINT ck_chat_message_status
        CHECK (status IN ('PENDING', 'GENERATING', 'STREAMING', 'COMPLETED', 'CANCELLED', 'FAILED')),
    CONSTRAINT ck_chat_message_token_count CHECK (token_count IS NULL OR token_count >= 0),
    CONSTRAINT ux_chat_message_sequence
        UNIQUE (owner_user_id, conversation_id, sequence)
);

CREATE TABLE chat_summary (
    id                        BIGSERIAL PRIMARY KEY,
    owner_user_id             BIGINT      NOT NULL,
    conversation_id          BIGINT      NOT NULL,
    covered_through_sequence  BIGINT      NOT NULL,
    content                   TEXT        NOT NULL,
    token_count               INTEGER,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_chat_summary_conversation_owner
        FOREIGN KEY (conversation_id, owner_user_id)
        REFERENCES chat_conversation (id, owner_user_id) ON DELETE CASCADE,
    CONSTRAINT ck_chat_summary_sequence CHECK (covered_through_sequence > 0),
    CONSTRAINT ck_chat_summary_token_count CHECK (token_count IS NULL OR token_count >= 0),
    CONSTRAINT ux_chat_summary_conversation
        UNIQUE (owner_user_id, conversation_id)
);

CREATE INDEX ix_knowledge_base_owner_status
    ON knowledge_base (owner_user_id, status, updated_at DESC);
CREATE INDEX ix_knowledge_document_owner_base_status
    ON knowledge_document (owner_user_id, knowledge_base_id, status, updated_at DESC);
CREATE INDEX ix_knowledge_document_owner_status
    ON knowledge_document (owner_user_id, status, updated_at DESC);
CREATE INDEX ix_knowledge_chunk_scope
    ON knowledge_chunk (owner_user_id, knowledge_base_id, document_id, index_version);
CREATE INDEX ix_knowledge_chunk_lexical
    ON knowledge_chunk USING GIN (search_vector);
CREATE INDEX ix_knowledge_chunk_embedding_hnsw
    ON knowledge_chunk USING HNSW (embedding vector_cosine_ops)
    WHERE embedding IS NOT NULL;
CREATE INDEX ix_knowledge_job_claim
    ON knowledge_index_job (status, next_retry_at, created_at)
    WHERE status IN ('PENDING', 'PROCESSING');
CREATE INDEX ix_knowledge_job_owner_document
    ON knowledge_index_job (owner_user_id, knowledge_base_id, document_id, created_at DESC);
CREATE INDEX ix_knowledge_document_index_ready
    ON knowledge_document_index (owner_user_id, backend, status, index_version);
CREATE INDEX ix_chat_conversation_owner
    ON chat_conversation (owner_user_id, status, updated_at DESC);
CREATE INDEX ix_chat_conversation_kb_owner
    ON chat_conversation_kb (owner_user_id, knowledge_base_id, conversation_id);
CREATE INDEX ix_chat_message_history
    ON chat_message (owner_user_id, conversation_id, sequence);
CREATE INDEX ix_chat_summary_latest
    ON chat_summary (owner_user_id, conversation_id, covered_through_sequence DESC);

COMMENT ON TABLE knowledge_base IS 'User-owned knowledge base; owner is always derived from JWT authentication';
COMMENT ON TABLE knowledge_document IS 'Knowledge source metadata and extracted text';
COMMENT ON TABLE knowledge_document_blob IS 'Original uploaded bytes retained in PostgreSQL for deterministic reindex and deletion';
COMMENT ON TABLE knowledge_chunk IS 'Parent/child chunks with persistent lexical and pgvector representations';
COMMENT ON TABLE knowledge_index_job IS 'Durable work queue consumed by the later indexing worker';
COMMENT ON TABLE knowledge_document_index IS 'Per-backend/model/version build state used for safe index switching';
COMMENT ON TABLE chat_message IS 'Complete immutable-visible conversation history; summaries never replace these rows';
