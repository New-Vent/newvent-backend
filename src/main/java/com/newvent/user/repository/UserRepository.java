package com.newvent.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.newvent.user.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);
}
