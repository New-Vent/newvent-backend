-- Refresh Token 저장소.
--
-- 1. 토큰 원문은 저장하지 않는다. token_hash = SHA-256(원문) hex (64자).
-- 2. 로그인 주체가 users / admins 두 테이블로 나뉘어 있으므로 user_id, admin_id 중
--    정확히 하나만 채운다 (ck_refresh_tokens_owner). 둘 다 외래 키라 계정이 삭제되면 토큰도 함께 지워진다.
-- 3. used_at: Rotation 에 한 번 쓰인 시각. NULL = 아직 안 씀.
--    값이 있는 토큰이 다시 들어오면 탈취/재사용으로 보고 그 계정의 토큰을 전부 폐기한다.

CREATE TABLE refresh_tokens
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT,
    admin_id   BIGINT,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_refresh_tokens_admin
        FOREIGN KEY (admin_id) REFERENCES admins (id)
            ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT ck_refresh_tokens_owner
        CHECK ((user_id IS NULL) <> (admin_id IS NULL))
);

-- 재사용 탐지 시 계정별 전체 폐기
CREATE INDEX idx_refresh_tokens_user_id
    ON refresh_tokens (user_id);

CREATE INDEX idx_refresh_tokens_admin_id
    ON refresh_tokens (admin_id);

-- 만료 토큰 정리 배치
CREATE INDEX idx_refresh_tokens_expires_at
    ON refresh_tokens (expires_at);
