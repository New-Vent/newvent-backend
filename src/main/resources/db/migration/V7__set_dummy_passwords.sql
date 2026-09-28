-- V3 더미 계정의 자리표시 해시('{bcrypt}REPLACE_WITH_REAL_HASH')를 실제 BCrypt 해시로 교체
--
-- V3 는 이미 적용된 DB 가 있어 직접 고치면 체크섬이 어긋난다. 그래서 여기서 UPDATE 한다.
--
-- 개발용 비밀번호 (로그인 테스트용 — 운영 DB 에서는 반드시 바꿀 것)
--   관리자  admin            / admin1234!
--   사용자  user01 ~ (전원)  / user1234!
--
-- 1. pgcrypto 의 crypt(..., gen_salt('bf', 10)) 는 $2a$10$... 형식이라 BCryptPasswordEncoder 와 호환된다.
-- 2. '{bcrypt}' 접두사는 붙이지 않는다. SecurityConfig 가 DelegatingPasswordEncoder 가 아닌
--    BCryptPasswordEncoder 를 직접 쓰므로, 접두사가 있으면 해시가 맞아도 로그인에 실패한다.
-- 3. 자리표시 값인 행만 바꾼다 — 회원가입으로 실제 비밀번호를 가진 계정은 건드리지 않는다.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

UPDATE admins
SET password_hash = crypt('admin1234!', gen_salt('bf', 10))
WHERE password_hash = '{bcrypt}REPLACE_WITH_REAL_HASH';

UPDATE users
SET password_hash = crypt('user1234!', gen_salt('bf', 10))
WHERE password_hash = '{bcrypt}REPLACE_WITH_REAL_HASH';
