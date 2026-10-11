package com.newvent.user.service;

import java.time.LocalDate;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.common.response.PageResponse;
import com.newvent.user.domain.MembershipGrade;
import com.newvent.user.domain.User;
import com.newvent.user.dto.response.AdminUserSummaryResponse;
import com.newvent.user.exception.DuplicateUserException;
import com.newvent.user.exception.UserNotFoundException;
import com.newvent.user.exception.code.UserErrorCode;
import com.newvent.user.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class UserService {

    // 가입자는 요금제를 고를 수 없다 - 모두 이 요금제(NORMAL)로 시작하고, 변경은 관리자가 한다
    static final int SIGNUP_DEFAULT_PLAN = 35_000;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User signUp(String loginId, String rawPassword, String name, String email, String phone) {
        if (userRepository.existsByLoginId(loginId)) {
            throw new DuplicateUserException(UserErrorCode.DUPLICATE_LOGIN_ID);
        }
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateUserException(UserErrorCode.DUPLICATE_EMAIL);
        }

        String passwordHash = passwordEncoder.encode(rawPassword);
        MembershipGrade membershipGrade = MembershipGradeService.onSignUp(SIGNUP_DEFAULT_PLAN, LocalDate.now());

        User user = new User(loginId, passwordHash, name, email, phone, SIGNUP_DEFAULT_PLAN, membershipGrade);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return findByIdOrThrow(id);
    }

    @Transactional
    public User updateProfile(Long id, String name, String email, String phone) {
        User user = findByIdOrThrow(id);
        if (email != null && !email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new DuplicateUserException(UserErrorCode.DUPLICATE_EMAIL);
        }
        user.updateProfile(name, email, phone);
        return user;
    }

    // 관리자만 부른다 - 사용자가 스스로 요금제를 바꿀 방법 x 바뀐 요금제로 등급을 바로 다시 계산
    @Transactional
    public User changePlanByAdmin(Long adminId, Long userId, int newPlan) {
        User user = findByIdOrThrow(userId);
        int oldPlan = user.getPlan();
        MembershipGrade oldGrade = user.getMembershipGrade();
        MembershipGrade recalculatedGrade = MembershipGradeService.onPlanChanged(newPlan, user.joinedAt());
        user.changePlan(newPlan, recalculatedGrade);
        log.info("관리자 {} 가 사용자 {} 의 요금제를 변경했습니다: {} -> {}, 등급 {} -> {}",
                adminId, userId, oldPlan, newPlan, oldGrade, recalculatedGrade);
        return user;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserSummaryResponse> searchUsers(
            String keyword, MembershipGrade grade, int page, int size) {
        Page<User> users = userRepository.searchUsers(likePattern(keyword), grade, page, size);
        return PageResponse.of(
                users.getContent().stream().map(AdminUserSummaryResponse::from).toList(),
                page, size, users.getTotalElements());
    }

    // 검색어를 소문자 "%값%" LIKE 패턴으로 만든다. 값 안의 % _ 는 글자 그대로 찾도록 이스케이프. 비면 조건 없음(null)
    static String likePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escape = String.valueOf(UserRepository.LIKE_ESCAPE);
        String escaped = keyword.strip().toLowerCase(Locale.ROOT)
                .replace(escape, escape + escape)
                .replace("%", escape + "%")
                .replace("_", escape + "_");
        return "%" + escaped + "%";
    }

    private User findByIdOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(UserNotFoundException::new);
    }
}
