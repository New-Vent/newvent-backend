-- New-Vent initial schema
-- PostgreSQL / Flyway
--
-- Notes
-- 1. ENUM 컬럼은 VARCHAR로 저장한다.
--    값이 확정된 컬럼은 CHECK 제약조건으로 허용값을 제한하고,
--    애플리케이션에서는 @Enumerated(EnumType.STRING)을 사용한다.
-- 2. 외래 키 컬럼은 독립 시퀀스가 필요한 BIGSERIAL이 아니라 BIGINT를 사용한다.
-- 3. events.published_version_id와 event_versions.event_id가 서로 참조하므로
--    published_version_id의 외래 키는 두 테이블 생성 후 추가한다.

CREATE TABLE admins
(
    id            BIGSERIAL PRIMARY KEY,
    login_id      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name          VARCHAR(50)  NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_admins_login_id UNIQUE (login_id)
);

CREATE TABLE users
(
    id               BIGSERIAL PRIMARY KEY,
    login_id         VARCHAR(50)  NOT NULL,
    password_hash    VARCHAR(255) NOT NULL,
    name             VARCHAR(50)  NOT NULL,
    email            VARCHAR(100) NOT NULL,
    phone            VARCHAR(20),
    plan             INTEGER      NOT NULL,
    membership_grade VARCHAR(20)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_users_login_id UNIQUE (login_id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_plan
        CHECK (plan > 0),
    CONSTRAINT ck_users_membership_grade
        CHECK (membership_grade IN ('NORMAL', 'EXCELLENT', 'BEST'))
);

CREATE TABLE event_templates
(
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(50)  NOT NULL,
    name         VARCHAR(100) NOT NULL,
    description  VARCHAR(255),
    html_content   TEXT         NOT NULL,
    is_builtin     BOOLEAN      NOT NULL DEFAULT FALSE,
    thumbnail_path VARCHAR(255),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_event_templates_code UNIQUE (code)
);

CREATE TABLE games
(
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(30) NOT NULL,
    name        VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_games_code UNIQUE (code)
);

CREATE TABLE events
(
    id                   BIGSERIAL PRIMARY KEY,
    owner_admin_id       BIGINT       NOT NULL,
    template_id          BIGINT,
    title                VARCHAR(100) NOT NULL,
    start_date           TIMESTAMPTZ,
    end_date             TIMESTAMPTZ,
    status               VARCHAR(30)  NOT NULL,
    review_status        VARCHAR(30)  NOT NULL,
    grade                VARCHAR(20)  NOT NULL,
    url                  TEXT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at           TIMESTAMPTZ,
    published_version_id BIGINT,

    CONSTRAINT fk_events_owner_admin
        FOREIGN KEY (owner_admin_id) REFERENCES admins (id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_events_template
        FOREIGN KEY (template_id) REFERENCES event_templates (id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_events_period
        CHECK (start_date IS NULL OR end_date IS NULL OR start_date <= end_date),
    CONSTRAINT uk_events_published_version_id UNIQUE (published_version_id),
    CONSTRAINT ck_events_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'ENDED')),
    CONSTRAINT ck_events_review_status
        CHECK (review_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_events_grade
        CHECK (grade IN ('NORMAL', 'EXCELLENT', 'BEST'))
);

CREATE TABLE chat_messages
(
    id           BIGSERIAL PRIMARY KEY,
    role         VARCHAR(30) NOT NULL,
    content      TEXT        NOT NULL,
    target_block VARCHAR(50),
    status       VARCHAR(30) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    event_id     BIGINT      NOT NULL,

    CONSTRAINT fk_chat_messages_event
        FOREIGN KEY (event_id) REFERENCES events (id)
            ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT ck_chat_messages_role
        CHECK (role IN ('ADMIN', 'ASSISTANT')),
    CONSTRAINT ck_chat_messages_status
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_chat_messages_event_created_id
    ON chat_messages (event_id, created_at, id);

CREATE TABLE event_versions
(
    id                 BIGSERIAL PRIMARY KEY,
    version_no         INTEGER     NOT NULL,
    html_content       TEXT        NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_checkpoint      BOOLEAN     NOT NULL DEFAULT FALSE,
    checkpointed_at    TIMESTAMPTZ,
    event_id           BIGINT      NOT NULL,
    source_version_id  BIGINT,
    request_message_id BIGINT,

    CONSTRAINT fk_event_versions_event
        FOREIGN KEY (event_id) REFERENCES events (id)
            ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_event_versions_source_version
        FOREIGN KEY (source_version_id) REFERENCES event_versions (id)
            ON UPDATE RESTRICT ON DELETE SET NULL,
    CONSTRAINT fk_event_versions_request_message
        FOREIGN KEY (request_message_id) REFERENCES chat_messages (id)
            ON UPDATE RESTRICT ON DELETE SET NULL,
    CONSTRAINT uk_event_versions_event_version_no
        UNIQUE (event_id, version_no),
    CONSTRAINT uk_event_versions_request_message_id
        UNIQUE (request_message_id),
    CONSTRAINT ck_event_versions_version_no
        CHECK (version_no > 0),
    CONSTRAINT ck_event_versions_checkpointed_at
        CHECK (
            (is_checkpoint = FALSE AND checkpointed_at IS NULL)
                OR
            (is_checkpoint = TRUE AND checkpointed_at IS NOT NULL)
            )
);

ALTER TABLE events
    ADD CONSTRAINT fk_events_published_version
        FOREIGN KEY (published_version_id) REFERENCES event_versions (id)
            ON UPDATE RESTRICT ON DELETE SET NULL;

CREATE TABLE llm_call_logs
(
    id               BIGSERIAL PRIMARY KEY,
    event_id         BIGINT      NOT NULL,
    version_id       BIGINT,
    request_id       UUID        NOT NULL,
    attempt_no       INTEGER     NOT NULL,
    model_name       VARCHAR(100) NOT NULL,
    success          BOOLEAN     NOT NULL,
    failure_type     VARCHAR(50),
    failure_message  TEXT,
    response_time_ms INTEGER,
    input_tokens     INTEGER,
    output_tokens    INTEGER,
    truncated        BOOLEAN NOT NULL DEFAULT FALSE,
    provider         VARCHAR(20) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_llm_call_logs_event
        FOREIGN KEY (event_id) REFERENCES events (id)
            ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_llm_call_logs_version
        FOREIGN KEY (version_id) REFERENCES event_versions (id)
            ON UPDATE RESTRICT ON DELETE SET NULL,
    CONSTRAINT uk_llm_call_logs_request_attempt
        UNIQUE (request_id, attempt_no),
    CONSTRAINT ck_llm_call_logs_attempt_no
        CHECK (attempt_no > 0),
    CONSTRAINT ck_llm_call_logs_response_time_ms
        CHECK (response_time_ms IS NULL OR response_time_ms >= 0),
    CONSTRAINT ck_llm_call_logs_input_tokens
        CHECK (input_tokens IS NULL OR input_tokens >= 0),
    CONSTRAINT ck_llm_call_logs_output_tokens
        CHECK (output_tokens IS NULL OR output_tokens >= 0),
    CONSTRAINT ck_llm_call_logs_failure_fields
        CHECK (
            (success = TRUE AND failure_type IS NULL AND failure_message IS NULL)
                OR success = FALSE
            )
);

CREATE INDEX idx_llm_call_logs_event_created_at
    ON llm_call_logs (event_id, created_at);

CREATE TABLE event_participations
(
    id             BIGSERIAL PRIMARY KEY,
    submitted_data JSONB       NOT NULL DEFAULT '{}'::JSONB,
    result_data    JSONB       NOT NULL DEFAULT '{}'::JSONB,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id        BIGINT      NOT NULL,
    event_id       BIGINT      NOT NULL,

    CONSTRAINT fk_event_participations_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_event_participations_event
        FOREIGN KEY (event_id) REFERENCES events (id)
            ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT uk_event_participations_event_user
        UNIQUE (event_id, user_id)
);

CREATE INDEX idx_event_participations_user_created_at
    ON event_participations (user_id, created_at);

CREATE TABLE event_game_configs
(
    id         BIGSERIAL PRIMARY KEY,
    config     JSONB       NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    game_id    BIGINT      NOT NULL,
    event_id   BIGINT      NOT NULL,

    CONSTRAINT fk_event_game_configs_game
        FOREIGN KEY (game_id) REFERENCES games (id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_event_game_configs_event
        FOREIGN KEY (event_id) REFERENCES events (id)
            ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT uk_event_game_configs_event_game
        UNIQUE (event_id, game_id)
);

CREATE INDEX idx_events_owner_admin_created_at
    ON events (owner_admin_id, created_at);

CREATE INDEX idx_events_status_period
    ON events (status, start_date, end_date) WHERE deleted_at IS NULL;

CREATE INDEX idx_event_versions_event_created_at
    ON event_versions (event_id, created_at);
