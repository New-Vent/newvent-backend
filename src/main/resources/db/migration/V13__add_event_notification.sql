-- 이벤트 시작·마감임박·종료 알림 (이슈 #104).
--
-- 1. events 에 알림별 발송 시각을 하나씩 둔다. NULL = 아직 안 보냄.
--    같은 이벤트에 같은 종류의 알림이 두 번 가지 않도록 스케줄러가 이 값으로 막는다.
-- 2. 배포 시점에 이미 지나간 마일스톤까지 알림이 한꺼번에 쏟아지지 않도록,
--    "이미 지난 것"만 지금 시각으로 채워 둔다 — 아직 안 지난 건 그대로 NULL로 둬서
--    실제 그 시점에 정상적으로 알림이 가게 한다.

ALTER TABLE events
    ADD COLUMN start_notified_at        TIMESTAMPTZ,
    ADD COLUMN closing_soon_notified_at TIMESTAMPTZ,
    ADD COLUMN end_notified_at          TIMESTAMPTZ;

-- status = PUBLISHED로 좁힌다 — 실제 알림 쿼리도 PUBLISHED만 본다.
-- 이게 없으면 "시작일이 과거인 DRAFT 이벤트"가 나중에 게시될 때
-- 이미 start_notified_at이 채워져 있어서 정당한 "시작" 알림을 영영 못 받는다.
UPDATE events
SET start_notified_at = CURRENT_TIMESTAMP
WHERE status = 'PUBLISHED'
  AND start_date IS NOT NULL
  AND start_date <= CURRENT_TIMESTAMP;

UPDATE events
SET closing_soon_notified_at = CURRENT_TIMESTAMP
WHERE status = 'PUBLISHED'
  AND end_date IS NOT NULL
  AND end_date <= CURRENT_TIMESTAMP + INTERVAL '3 days';

UPDATE events
SET end_notified_at = CURRENT_TIMESTAMP
WHERE status = 'ENDED';

CREATE TABLE admin_notifications
(
    id         BIGSERIAL PRIMARY KEY,
    admin_id   BIGINT      NOT NULL,
    event_id   BIGINT      NOT NULL,
    type       VARCHAR(20) NOT NULL,
    message    TEXT        NOT NULL,
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_admin_notifications_admin
        FOREIGN KEY (admin_id) REFERENCES admins (id)
            ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_admin_notifications_event
        FOREIGN KEY (event_id) REFERENCES events (id)
            ON UPDATE RESTRICT ON DELETE CASCADE
);

-- 알림함 조회 — 관리자별 최신순
CREATE INDEX idx_admin_notifications_admin_id
    ON admin_notifications (admin_id, created_at DESC);
