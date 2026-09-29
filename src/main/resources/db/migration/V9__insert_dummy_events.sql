-- 관리자 이벤트 JPA 전환 (feat/51-admin-event-jpa)
-- InMemoryEventRepository 에 있던 더미 이벤트 7건을 실제 DB로 옮긴다.
-- id=99 는 휴지통 기능(소프트 삭제(deleted_at 있음))의 조회·복구 기능을 만들 때 쓸 수 있어 그대로 옮긴다.
--
-- owner_admin_id 는 V3 에서 넣은 관리자(login_id='admin') 계정을 그대로 쓴다 — 인증 연동 전
-- InMemoryEventRepository.systemAdmin() 이 하던 "이벤트 소유자" 역할을 대신한다.
-- template_id 는 기존 인메모리 시드에서도 전부 null 이었으므로 그대로 null 로 둔다.
--
-- events.id 를 기존 인메모리 id(1~6, 99)와 그대로 맞춰서 팀이 알던 번호를 유지하고,
-- 뒤에서 시퀀스를 MAX(id) 로 재조정해 다음 생성 이벤트와 충돌하지 않게 한다.

INSERT INTO events (id, owner_admin_id, template_id, title, start_date, end_date, status, grade, created_at, updated_at, deleted_at) VALUES
(1, (SELECT id FROM admins WHERE login_id = 'admin'), NULL, '신규 가입 데이터 쿠폰 3GB', '2026-09-16T00:00:00+09:00', '2026-10-15T23:59:59+09:00', 'PUBLISHED', 'NORMAL', '2026-09-16T10:20:00+09:00', '2026-09-16T10:20:00+09:00', NULL),
(2, (SELECT id FROM admins WHERE login_id = 'admin'), NULL, '월드컵 스코어 맞추기', '2026-07-01T00:00:00+09:00', '2026-07-31T23:59:59+09:00', 'DRAFT', 'EXCELLENT', '2026-06-28T16:00:00+09:00', '2026-06-28T16:00:00+09:00', NULL),
(3, (SELECT id FROM admins WHERE login_id = 'admin'), NULL, '지금 긁으면 바로 당첨', '2026-09-16T00:00:00+09:00', '2026-09-18T23:59:59+09:00', 'PUBLISHED', 'NORMAL', '2026-09-15T09:10:00+09:00', '2026-09-15T09:10:00+09:00', NULL),
(4, (SELECT id FROM admins WHERE login_id = 'admin'), NULL, '가을 멤버십 더블 혜택', '2026-09-01T00:00:00+09:00', '2026-11-30T23:59:59+09:00', 'PUBLISHED', 'NORMAL', '2026-08-30T11:40:00+09:00', '2026-08-30T11:40:00+09:00', NULL),
(5, (SELECT id FROM admins WHERE login_id = 'admin'), NULL, '새 요금제 사전예약', '2026-10-01T00:00:00+09:00', '2026-10-20T23:59:59+09:00', 'DRAFT', 'NORMAL', '2026-09-12T14:05:00+09:00', '2026-09-12T14:05:00+09:00', NULL),
(6, (SELECT id FROM admins WHERE login_id = 'admin'), NULL, '여름 데이터 대방출', '2026-06-01T00:00:00+09:00', '2026-08-31T23:59:59+09:00', 'ENDED', 'NORMAL', '2026-09-01T08:00:00+09:00', '2026-09-01T08:00:00+09:00', NULL),
(99, (SELECT id FROM admins WHERE login_id = 'admin'), NULL, '삭제된 이벤트', '2026-01-01T00:00:00+09:00', '2026-01-10T23:59:59+09:00', 'DRAFT', 'NORMAL', '2026-01-11T00:00:00+09:00', '2026-01-11T00:00:00+09:00', '2026-01-11T00:00:00+09:00');

SELECT setval('events_id_seq', (SELECT MAX(id) FROM events));

-- 완성된 HTML 이 있던 이벤트(1, 3, 4, 6)만 event_versions 를 만들고 published_version_id 로 연결한다.
INSERT INTO event_versions (version_no, html_content, event_id) VALUES
(1, '<section data-block="hero"><h1>신규 가입 데이터 쿠폰 3GB</h1></section>', 1),
(1, '<section data-block="hero"><h1>지금 긁으면 바로 당첨</h1></section>', 3),
(1, '<section data-block="hero"><h1>가을 멤버십 더블 혜택</h1></section>', 4),
(1, '<section data-block="hero"><h1>여름 데이터 대방출</h1></section>', 6);

UPDATE events e
SET published_version_id = v.id
FROM event_versions v
WHERE v.event_id = e.id;
