-- RAG 재색인·청킹 결함 수정 묶음.
--
-- 1. ck_rag_chunks_block_key 확장: 청킹이 뱉는 stats·prize·coupon·schedule 4종을 허용한다.
--    이 4종이 하나라도 있으면 saveAll 이 CHECK 위반으로 통째로 롤백되어 그 버전 청크가 0개가 된다.
-- 2. uk_rag_chunks_version_block 추가: (이벤트, 버전, 블록, 순서) 중복 삽입을 DB 가 막는다.
--    단건 재색인과 전체 재색인이 같은 버전을 동시에 돌릴 때 마지막 보루다.
-- 3. fk_rag_chunks_version 을 SET NULL → CASCADE 로 교체: 버전 삭제 시 고아 청크가 남지 않는다.
--    SET NULL 상태에서는 버전이 없어져도 청크가 계속 검색에 노출된다.
--
-- ★ uk 추가 전에 중복 행이 있으면 마이그레이션이 실패한다. 운영 DB 에서 0건인지 확인할 것.
--     SELECT event_id, version_id, block_key, chunk_index, count(*)
--       FROM rag_chunks GROUP BY 1, 2, 3, 4 HAVING count(*) > 1;
ALTER TABLE rag_chunks DROP CONSTRAINT ck_rag_chunks_block_key;

ALTER TABLE rag_chunks ADD CONSTRAINT ck_rag_chunks_block_key
    CHECK (block_key IN ('hero', 'highlight', 'intro', 'stats', 'benefits',
        'prize', 'coupon', 'compare', 'audience', 'steps',
        'schedule', 'faq', 'notices', 'cta'));

ALTER TABLE rag_chunks ADD CONSTRAINT uk_rag_chunks_version_block
    UNIQUE (event_id, version_id, block_key, chunk_index);

ALTER TABLE rag_chunks DROP CONSTRAINT fk_rag_chunks_version;

ALTER TABLE rag_chunks ADD CONSTRAINT fk_rag_chunks_version
    FOREIGN KEY (version_id) REFERENCES event_versions (id)
        ON UPDATE RESTRICT ON DELETE CASCADE;
