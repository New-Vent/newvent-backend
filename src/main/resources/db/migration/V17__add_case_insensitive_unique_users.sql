-- 대소문자만 다른 아이디·이메일(USER01 / user01)로 중복 가입되는 것을 DB 에서도 막는다.
--
-- 기존 uk_users_login_id, uk_users_email 은 대소문자를 구분하는 UNIQUE 라 그대로 둔다(더 느슨한 제약이라 해가 없다).
-- 아이디·이메일은 입력한 대로 저장하고, 중복 판정만 소문자로 맞춰 비교한다.
--
-- ★ 이미 대소문자만 다른 행이 있으면 이 마이그레이션이 실패해 서버가 시작하지 못한다.
--   배포 전에 아래 두 쿼리가 0건인지 확인할 것.
--     SELECT lower(login_id), count(*) FROM users GROUP BY 1 HAVING count(*) > 1;
--     SELECT lower(email),    count(*) FROM users GROUP BY 1 HAVING count(*) > 1;
CREATE UNIQUE INDEX uk_users_login_id_lower ON users (lower(login_id));
CREATE UNIQUE INDEX uk_users_email_lower ON users (lower(email));
