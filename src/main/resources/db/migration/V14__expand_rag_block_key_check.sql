-- #111 #118 에서 Block 이 10개로 늘었는데 V10 CHECK 는 5개 그대로라
-- highlight/intro/compare/audience/faq 청크 저장 시 CHECK 위반으로 색인이 통째로 실패한다.
-- V10 은 이미 적용된 마이그레이션이라 고치지 않고 여기서 교체한다.
-- notices 는 SERVER 소스라 색인 안 되지만 DB 제약에는 남겨둔다 (기존 행 호환).
ALTER TABLE rag_chunks DROP CONSTRAINT ck_rag_chunks_block_key;

ALTER TABLE rag_chunks ADD CONSTRAINT ck_rag_chunks_block_key
    CHECK (block_key IN ('hero', 'highlight', 'intro', 'benefits', 'compare', 'audience', 'steps', 'faq', 'notices', 'cta'));
