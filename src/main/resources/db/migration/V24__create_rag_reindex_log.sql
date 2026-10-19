-- 재색인 기록. 재색인은 4대 기능 중 복구 액션이다.
--
-- ★ 전체 재색인 진행 상태는 인메모리(volatile)다. 서버가 내려가면 이력이 사라지고,
--   남는 흔적이라고는 rag_chunks.created_at 이 앞당겨지는 것뿐이라
--   품질 추이에서 "이 주 청크가 왜 튀었나"를 아무도 설명할 수 없다.
--   그래서 실행 1회당 한 줄을 남긴다.
--
-- ★ 추가 전용 표다. 수정·삭제는 없고 행이 쌓이기만 한다 (버튼 클릭 빈도라 저빈도).
--   범위 scope: SINGLE(버전 하나) / EVENT(이벤트 전체 버전) / ALL(전체 재색인)
--   상태 status: SUCCESS / FAILED
--
-- ★ 이벤트·버전은 지워져도 이력은 남는다 — 참조 끊긴 로그로 남긴다 (ON DELETE SET NULL).
CREATE TABLE rag_reindex_log
(
    id              BIGSERIAL PRIMARY KEY,
    scope           VARCHAR(16)  NOT NULL,
    event_id        BIGINT,
    version_id      BIGINT,
    requested_by    VARCHAR(64),
    chunk_count     INTEGER,
    duration_ms     INTEGER,
    status          VARCHAR(16)  NOT NULL,
    failure_message TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_rag_reindex_log_scope
        CHECK (scope IN ('SINGLE', 'EVENT', 'ALL')),
    CONSTRAINT ck_rag_reindex_log_status
        CHECK (status IN ('SUCCESS', 'FAILED')),
    CONSTRAINT fk_rag_reindex_log_event
        FOREIGN KEY (event_id) REFERENCES events (id)
            ON UPDATE RESTRICT ON DELETE SET NULL,
    CONSTRAINT fk_rag_reindex_log_version
        FOREIGN KEY (version_id) REFERENCES event_versions (id)
            ON UPDATE RESTRICT ON DELETE SET NULL
);

-- 목록은 created_at 내림차순으로 읽는다
CREATE INDEX idx_rag_reindex_log_created_at
    ON rag_reindex_log (created_at);
