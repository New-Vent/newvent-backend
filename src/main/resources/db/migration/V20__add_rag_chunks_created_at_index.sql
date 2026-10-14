-- 품질 추이 집계(countChunksByWeek)가 created_at 범위로 돈다. 테이블이 커지면 풀스캔이라 인덱스를 둔다.
-- llm_call_logs 쪽은 V1 에 idx_llm_call_logs_created_at 이 이미 있다.
CREATE INDEX IF NOT EXISTS idx_rag_chunks_created_at
    ON rag_chunks (created_at);
