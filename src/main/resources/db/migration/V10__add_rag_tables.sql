-- RAG 검색용 임베딩 청크 테이블.
--
-- ★ embedding_model 을 함께 저장한다.
--   모델을 바꾸면 기존 벡터와 공간이 안 맞아 전부 재색인이다.
--   조회 시 모델로 필터해서 섞이지 않게 한다.
--
-- ★ CREATE EXTENSION 은 권한 있는 유저로 1회 실행이 필요하다.
--   로컬 compose PG(superuser)는 그대로 돈다.
--   RDS는 마스터 유저로 미리 켜둬야 한다 (인프라 담당).
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE rag_chunks
(
    id              BIGSERIAL PRIMARY KEY,
    event_id        BIGINT       NOT NULL,
    version_id      BIGINT,
    block_key       VARCHAR(30)  NOT NULL,
    chunk_index     INTEGER      NOT NULL,
    content         TEXT         NOT NULL,
    embedding_model VARCHAR(80)  NOT NULL,
    embedding       vector(1024) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_rag_chunks_event
        FOREIGN KEY (event_id) REFERENCES events (id)
            ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_rag_chunks_version
        FOREIGN KEY (version_id) REFERENCES event_versions (id)
            ON UPDATE RESTRICT ON DELETE SET NULL,

    CONSTRAINT ck_rag_chunks_block_key
        CHECK (block_key IN ('hero', 'benefits', 'steps', 'notices', 'cta'))
);

CREATE INDEX idx_rag_chunks_embedding
    ON rag_chunks
    USING hnsw (embedding vector_cosine_ops);

CREATE INDEX idx_rag_chunks_event
    ON rag_chunks (event_id);