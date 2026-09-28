package com.newvent.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.newvent.user.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);
}
