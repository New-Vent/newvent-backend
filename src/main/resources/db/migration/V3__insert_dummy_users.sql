-- 더미데이터(사용자) — 2026-09-21 스키마 변경 반영 (admins/users 분리, plan/membership_grade 재구성)
-- password_hash는 실제 해시가 아닙니다. 로그인 테스트가 필요해지면 BCryptPasswordEncoder로 생성한 값으로 교체하세요.
-- name/email/phone은 ERD 신규 컬럼이라 실제 값 없이 테스트용으로 새로 채운 값입니다.
--
-- 요금제 구간 (원본 매트릭스의 "4~6만원대"/"6만원대 이상" 중첩을 아래 기준으로 확정 — 2026-09-16 팀 확인 완료)
--   라이트   : 40,000원 미만        (1점)
--   스탠다드 : 40,000 ~ 59,999원    (2점)
--   프리미엄 : 60,000원 이상        (3점)
--   가입기간 : 1년 미만(1점) / 1년 이상~5년 미만(2점) / 5년 이상(3점) — 이 부분은 원본 매트릭스에 모호함 없음
--     ※ 가입기간 경과는 '월' 단위로 판정 (연·월 차이만 계산, 일자는 버림). created_at 자체는 실제 날짜(일 단위)를 그대로 저장.
--     기준월 2026-09 기준: 2025-09(포함)부터 12개월 경과 = 1년 이상, 2021-09(포함)부터 60개월 경과 = 5년 이상
--
-- membership_grade는 위 기준으로 plan+created_at을 계산한 "가입 시점" 값을 그대로 넣어둔 것입니다.
-- (2026-09-22 결정) 실제 서비스에서는 회원가입·요금제 변경·로그인 시점에만 재계산해서 이 컬럼에
-- 저장하며, 그 외 조회(GET)에서는 저장된 값을 그대로 신뢰합니다. 조회할 때마다 재계산하지 않습니다
-- — com.newvent.user.MembershipGradeService 참고.
--
-- 구성: 매트릭스 9칸(원본) + 경계값 9건(신규) + 목록화면 테스트용 10건(신규) = users 28건 + admins 1건

INSERT INTO admins (login_id, password_hash, name) VALUES
('admin', '{bcrypt}REPLACE_WITH_REAL_HASH', '관리자');

INSERT INTO users (login_id, password_hash, name, email, phone, plan, membership_grade, created_at) VALUES

-- ── 매트릭스 9칸 (등급 산정 로직 기본 검증용, 기존 유지) ──────
('user01', '{bcrypt}REPLACE_WITH_REAL_HASH', '테스트유저01', 'user01@test.newvent.io',   '010-0000-0001', 25000, 'NORMAL',   '2026-01-10'),
('user02', '{bcrypt}REPLACE_WITH_REAL_HASH', '테스트유저02', 'user02@test.newvent.io',   '010-0000-0002', 50000, 'NORMAL',   '2026-02-15'),
('user03', '{bcrypt}REPLACE_WITH_REAL_HASH', '테스트유저03', 'user03@test.newvent.io',   '010-0000-0003', 70000, 'EXCELLENT',   '2026-01-20'),
('user04', '{bcrypt}REPLACE_WITH_REAL_HASH', '테스트유저04', 'user04@test.newvent.io',   '010-0000-0004', 25000, 'NORMAL',   '2023-05-01'),
('user05', '{bcrypt}REPLACE_WITH_REAL_HASH', '테스트유저05', 'user05@test.newvent.io',   '010-0000-0005', 50000, 'EXCELLENT',   '2023-06-10'),
('user06', '{bcrypt}REPLACE_WITH_REAL_HASH', '테스트유저06', 'user06@test.newvent.io',   '010-0000-0006', 70000, 'EXCELLENT',   '2023-07-05'),
('user07', '{bcrypt}REPLACE_WITH_REAL_HASH', '테스트유저07', 'user07@test.newvent.io',   '010-0000-0007', 25000, 'EXCELLENT',   '2019-03-01'),
('user08', '{bcrypt}REPLACE_WITH_REAL_HASH', '테스트유저08', 'user08@test.newvent.io',   '010-0000-0008', 50000, 'EXCELLENT',   '2018-11-20'),
('user09', '{bcrypt}REPLACE_WITH_REAL_HASH', '테스트유저09', 'user09@test.newvent.io',   '010-0000-0009', 70000, 'BEST', '2017-08-15'),

-- ── 경계값 케이스 (등급 산정 로직의 <, <= 처리 검증용) ────────
-- 요금제 축만 경계 이동 (가입기간은 1~5년으로 고정 = 2점)
('boundary_fee_light_max',    '{bcrypt}REPLACE_WITH_REAL_HASH', '경계값유저10', 'boundary_fee_light_max@test.newvent.io',    '010-0000-0010', 39999, 'NORMAL', '2023-01-01'),  -- 라이트 상한
('boundary_fee_standard_min', '{bcrypt}REPLACE_WITH_REAL_HASH', '경계값유저11', 'boundary_fee_standard_min@test.newvent.io', '010-0000-0011', 40000, 'EXCELLENT', '2023-01-01'),  -- 스탠다드 하한
('boundary_fee_standard_max', '{bcrypt}REPLACE_WITH_REAL_HASH', '경계값유저12', 'boundary_fee_standard_max@test.newvent.io', '010-0000-0012', 59999, 'EXCELLENT', '2023-01-01'),  -- 스탠다드 상한
('boundary_fee_premium_min',  '{bcrypt}REPLACE_WITH_REAL_HASH', '경계값유저13', 'boundary_fee_premium_min@test.newvent.io',  '010-0000-0013', 60000, 'EXCELLENT', '2023-01-01'),  -- 프리미엄 하한

-- 가입기간 축만 경계 이동 (요금제는 스탠다드 50,000원으로 고정 = 2점)
-- 월 단위 판정이므로 일자가 달라도 같은 달이면 결과가 같아야 함 (boundary_join_1y_exact_eom으로 검증)
('boundary_join_1y_under',     '{bcrypt}REPLACE_WITH_REAL_HASH', '경계값유저14', 'boundary_join_1y_under@test.newvent.io',     '010-0000-0014', 50000, 'NORMAL', '2025-10-01'),  -- 11개월 경과, 1년 미만 마지막 달
('boundary_join_1y_exact',     '{bcrypt}REPLACE_WITH_REAL_HASH', '경계값유저15', 'boundary_join_1y_exact@test.newvent.io',     '010-0000-0015', 50000, 'EXCELLENT', '2025-09-01'),  -- 12개월 경과, 1년 이상 첫 달
('boundary_join_1y_exact_eom', '{bcrypt}REPLACE_WITH_REAL_HASH', '경계값유저16', 'boundary_join_1y_exact_eom@test.newvent.io', '010-0000-0016', 50000, 'EXCELLENT', '2025-09-30'),  -- 같은 달 말일 — 일자 달라도 동일 결과인지 검증
('boundary_join_5y_under',     '{bcrypt}REPLACE_WITH_REAL_HASH', '경계값유저17', 'boundary_join_5y_under@test.newvent.io',     '010-0000-0017', 50000, 'EXCELLENT', '2021-10-01'),  -- 59개월 경과, 5년 미만 마지막 달
('boundary_join_5y_exact',     '{bcrypt}REPLACE_WITH_REAL_HASH', '경계값유저18', 'boundary_join_5y_exact@test.newvent.io',     '010-0000-0018', 50000, 'EXCELLENT', '2021-09-01'),  -- 60개월 경과, 5년 이상 첫 달

-- ── 목록/페이지네이션 화면 테스트용 (그 외 다양한 조합) ────────
('user10', '{bcrypt}REPLACE_WITH_REAL_HASH', '테스트유저19', 'user10@test.newvent.io', '010-0000-0019', 30000, 'NORMAL',   '2024-03-15'),
('user11', '{bcrypt}REPLACE_WITH_REAL_HASH', '테스트유저20', 'user11@test.newvent.io', '010-0000-0020', 45000, 'NORMAL',   '2025-11-01'),
('user12', '{bcrypt}REPLACE_WITH_REAL_HASH', '테스트유저21', 'user12@test.newvent.io', '010-0000-0021', 65000, 'EXCELLENT',   '2022-08-20'),
('user13', '{bcrypt}REPLACE_WITH_REAL_HASH', '테스트유저22', 'user13@test.newvent.io', '010-0000-0022', 35000, 'EXCELLENT',   '2016-05-10'),
('user14', '{bcrypt}REPLACE_WITH_REAL_HASH', '테스트유저23', 'user14@test.newvent.io', '010-0000-0023', 55000, 'EXCELLENT',   '2024-12-01'),
('user15', '{bcrypt}REPLACE_WITH_REAL_HASH', '테스트유저24', 'user15@test.newvent.io', '010-0000-0024', 68000, 'BEST', '2015-02-14'),
('user16', '{bcrypt}REPLACE_WITH_REAL_HASH', '테스트유저25', 'user16@test.newvent.io', '010-0000-0025', 42000, 'EXCELLENT',   '2020-07-07'),
('user17', '{bcrypt}REPLACE_WITH_REAL_HASH', '테스트유저26', 'user17@test.newvent.io', '010-0000-0026', 28000, 'NORMAL',   '2025-04-01'),
('user18', '{bcrypt}REPLACE_WITH_REAL_HASH', '테스트유저27', 'user18@test.newvent.io', '010-0000-0027', 61000, 'EXCELLENT',   '2024-01-15'),
('user19', '{bcrypt}REPLACE_WITH_REAL_HASH', '테스트유저28', 'user19@test.newvent.io', '010-0000-0028', 48000, 'EXCELLENT',   '2019-09-09');
