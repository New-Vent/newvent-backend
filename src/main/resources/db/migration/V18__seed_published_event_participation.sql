-- 게시 중인 이벤트의 **초기** 참여 설정. 기본형(BASIC)으로 심는다.
--
-- ★ 왜 필요한가
--   설정이 없으면 ParticipationService 가 configs.size() != 1 로 걸러
--   409 PARTICIPATION409-1 을 낸다. 그래서 참여 API 는 완성돼 있는데
--   **한 번도 성공할 수 없는** 상태였다. 이 시드가 첫 값을 넣는다.
--
-- ★ 이 시드는 지우지 않는다 — 지울 수도 없다
--   관리자 설정 API(PUT /api/admin/events/{id}/participation-config)가 생기면
--   그걸로 **덮어쓰면** 된다. 설정은 이벤트당 하나라 upsert 가 자연스럽다.
--   V7(더미 비밀번호) · V8(기본 템플릿) · V9(더미 이벤트)와 같은 성격이다.
--
--   "나중에 지운다" 를 전제로 넣으면 안 된다. 뒤에 마이그레이션이 하나라도
--   생긴 뒤 이 파일을 지우면 Flyway 가 기동을 멈춘다 —
--   `Detected applied migration not resolved locally: 18`.
--   (맨 위일 때 지우면 경고만 나지만, 그 상태는 오래 유지되지 않는다.)
--
--   API 가 생긴 뒤에도 쓸모가 있다. 팀원이 각자 로컬에서 참여를 시험할 때
--   설정을 손으로 만들지 않아도 된다.
--
-- ★ 왜 BASIC 인가
--   추가 입력이 없어서 화면에 폼을 만들지 않아도 된다(ParticipationValidator.validateBasic).
--   resultMode=IMMEDIATE 라 참여하는 순간 서버가 추첨해 WON/LOST 가 바로 갈린다 —
--   event_participations.result_data 에 결과가 남는 것을 눈으로 확인할 수 있다.
--
-- ★ 이벤트 id 를 적지 않는다
--   id 는 환경마다 다르다. "게시 중이고 설정이 없는 것" 으로 고른다.
--   설정이 이미 있는 이벤트는 건드리지 않는다 — 한 이벤트에 설정은 하나여야 한다
--   (uk_event_game_configs_event_game, 그리고 서비스가 size() != 1 을 거부한다).
--
-- ★ 멱등이 아니다
--   이 마이그레이션은 한 번만 돈다. 나중에 게시되는 이벤트에는 설정이 붙지 않으므로,
--   관리자 설정 API 가 나오기 전까지는 새 이벤트 참여가 여전히 409 다.
INSERT INTO event_game_configs (event_id, game_id, config)
SELECT e.id,
       g.id,
       '{"resultMode":"IMMEDIATE","winProbability":30,"prizeName":"데이터 5GB 쿠폰"}'::JSONB
  FROM events e
  CROSS JOIN (SELECT id FROM games WHERE code = 'BASIC') g
 WHERE e.deleted_at IS NULL
   AND e.status = 'PUBLISHED'
   AND NOT EXISTS (SELECT 1 FROM event_game_configs c WHERE c.event_id = e.id);
