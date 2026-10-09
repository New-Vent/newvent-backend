package com.newvent.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.newvent.user.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);

    // 가입·이메일 변경의 중복 검사용 — 대소문자만 다른 아이디·이메일(USER01 / user01)도 같은 것으로 본다.
    // DB 에서도 lower(login_id), lower(email) 유니크 인덱스(V17)가 마지막으로 막는다.
    boolean existsByLoginIdIgnoreCase(String loginId);

    boolean existsByEmailIgnoreCase(String email);
}
