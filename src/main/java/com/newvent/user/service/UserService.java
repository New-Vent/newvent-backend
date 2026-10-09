package com.newvent.user.service;

import java.time.LocalDate;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.user.domain.MembershipGrade;
import com.newvent.user.domain.User;
import com.newvent.user.exception.DuplicateUserException;
import com.newvent.user.exception.UserNotFoundException;
import com.newvent.user.exception.code.UserErrorCode;
import com.newvent.user.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User signUp(String loginId, String rawPassword, String name, String email, String phone, int plan) {
        if (userRepository.existsByLoginIdIgnoreCase(loginId)) {
            throw new DuplicateUserException(UserErrorCode.DUPLICATE_LOGIN_ID);
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateUserException(UserErrorCode.DUPLICATE_EMAIL);
        }

        String passwordHash = passwordEncoder.encode(rawPassword);
        MembershipGrade membershipGrade = MembershipGradeService.onSignUp(plan, LocalDate.now());

        User user = new User(loginId, passwordHash, name, email, phone, plan, membershipGrade);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return findByIdOrThrow(id);
    }

    @Transactional
    public User updateProfile(Long id, String name, String email, String phone) {
        User user = findByIdOrThrow(id);
        // 본인 이메일의 대소문자만 바꾸는 경우(a@x.com → A@x.com)는 자기 자신과 겹치는 것이라 중복으로 보지 않는다
        if (email != null && !email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateUserException(UserErrorCode.DUPLICATE_EMAIL);
        }
        user.updateProfile(name, email, phone);
        return user;
    }

    @Transactional
    public User changePlan(Long id, int newPlan) {
        User user = findByIdOrThrow(id);
        MembershipGrade recalculatedGrade = MembershipGradeService.onPlanChanged(newPlan, user.joinedAt());
        user.changePlan(newPlan, recalculatedGrade);
        return user;
    }

    private User findByIdOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(UserNotFoundException::new);
    }
}
