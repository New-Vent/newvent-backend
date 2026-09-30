-- llm_call_logs 에 RAG 연결 필드 추가.
--
-- ★ 컬럼만 만든다. 값은 RAG 본 구현(b2/b3)이 색인·검색 때 채운다.
--   rag_used 는 NOT NULL DEFAULT FALSE 라 기존 행은 전부 false 로 깔린다.
--   chunk_ids 는 "이 호출이 어떤 청크(예시)를 썼는지" 콤마 구분 아이디 목록. 안 쓰면 null.
ALTER TABLE llm_call_logs ADD COLUMN rag_used BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE llm_call_logs ADD COLUMN chunk_ids TEXT;