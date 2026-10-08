package com.newvent.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.newvent.user.domain.MembershipGrade;
import com.newvent.user.domain.User;
import com.newvent.user.exception.DuplicateUserException;
import com.newvent.user.exception.UserNotFoundException;
import com.newvent.user.repository.UserRepository;

class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UserService userService = new UserService(userRepository, passwordEncoder);

    @Test
    @DisplayName("회원가입 시 비밀번호를 해싱하고, 기본 요금제로 시작해 일반 등급으로 저장한다")
    void 회원가입_비밀번호해싱과_등급계산() {
        when(userRepository.existsByLoginId("user01")).thenReturn(false);
        when(userRepository.existsByEmail("user01@test.com")).thenReturn(false);
        when(passwordEncoder.encode("raw-password")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User saved = userService.signUp(
                "user01", "raw-password", "테스트", "user01@test.com", "010-0000-0000");

        assertEquals("hashed-password", saved.getPasswordHash());
        // 기본 요금제(35,000원=1점) + 가입 당일(0개월=1점) = 2점 → NORMAL
        assertEquals(UserService.SIGNUP_DEFAULT_PLAN, saved.getPlan());
        assertEquals(MembershipGrade.NORMAL, saved.getMembershipGrade());
        verify(userRepository).save(saved);
    }

    @Test
    @DisplayName("이미 사용 중인 로그인 ID면 DuplicateUserException을 던진다")
    void 회원가입_로그인ID중복() {
        when(userRepository.existsByLoginId("dup")).thenReturn(true);

        assertThrows(
                DuplicateUserException.class,
                () -> userService.signUp("dup", "pw", "이름", "a@test.com", null));
    }

    @Test
    @DisplayName("이미 사용 중인 이메일이면 DuplicateUserException을 던진다")
    void 회원가입_이메일중복() {
        when(userRepository.existsByLoginId("new")).thenReturn(false);
        when(userRepository.existsByEmail("dup@test.com")).thenReturn(true);

        assertThrows(
                DuplicateUserException.class,
                () -> userService.signUp("new", "pw", "이름", "dup@test.com", null));
    }

    @Test
    @DisplayName("존재하는 id를 조회하면 해당 회원을 반환한다")
    void 조회_성공() {
        User user = new User("user01", "hash", "이름", "user01@test.com", null, 50000, MembershipGrade.NORMAL);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User found = userService.getById(1L);

        assertEquals(user, found);
    }

    @Test
    @DisplayName("존재하지 않는 id를 조회하면 UserNotFoundException을 던진다")
    void 조회_없는ID() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.getById(999L));
    }

    @Test
    @DisplayName("정보 수정 시 null로 넘어온 필드는 바꾸지 않는다")
    void 정보수정_부분수정() {
        User user =
                new User("user01", "hash", "기존이름", "old@test.com", "010-1111-1111", 50000, MembershipGrade.NORMAL);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User updated = userService.updateProfile(1L, null, null, "010-2222-2222");

        assertEquals("기존이름", updated.getName());
        assertEquals("old@test.com", updated.getEmail());
        assertEquals("010-2222-2222", updated.getPhone());
    }

    @Test
    @DisplayName("다른 사용자가 쓰는 이메일로 수정하면 DuplicateUserException을 던진다")
    void 정보수정_이메일중복() {
        User user = new User("user01", "hash", "이름", "old@test.com", null, 50000, MembershipGrade.NORMAL);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("taken@test.com")).thenReturn(true);

        assertThrows(
                DuplicateUserException.class, () -> userService.updateProfile(1L, null, "taken@test.com", null));
    }

    @Test
    @DisplayName("기본 요금제는 일반 등급이 나오는 값이다 - 가입만으로 등급을 올릴 수 없다")
    void 기본요금제는_일반등급() {
        MembershipGrade grade = MembershipGradeService.onSignUp(UserService.SIGNUP_DEFAULT_PLAN, LocalDate.now());

        assertEquals(MembershipGrade.NORMAL, grade);
    }

    @Test
    @DisplayName("관리자가 요금제를 변경하면 가입일 기준으로 등급을 재계산한다")
    void 관리자_요금제변경_등급재계산() {
        User user = new User("user01", "hash", "이름", "a@test.com", null, 25000, MembershipGrade.NORMAL);
        // @CreationTimestamp는 실제 영속화 시점에만 채워지므로, mock 단위테스트에서는 직접 주입한다.
        ReflectionTestUtils.setField(user, "createdAt", OffsetDateTime.now().minusYears(6));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User updated = userService.changePlanByAdmin(9L, 1L, 70000);

        // 70,000원(프리미엄=3점) + 5년 이상(3점) = 6점 → BEST
        assertEquals(70000, updated.getPlan());
        assertEquals(MembershipGrade.BEST, updated.getMembershipGrade());
    }

    @Test
    @DisplayName("관리자가 요금제를 내리면 등급도 함께 내려간다")
    void 관리자_요금제하향_등급하락() {
        User user = new User("user01", "hash", "이름", "a@test.com", null, 70000, MembershipGrade.BEST);
        ReflectionTestUtils.setField(user, "createdAt", OffsetDateTime.now().minusYears(6));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User updated = userService.changePlanByAdmin(9L, 1L, 25000);

        // 25,000원(1점) + 5년 이상(3점) = 4점 → EXCELLENT
        assertEquals(MembershipGrade.EXCELLENT, updated.getMembershipGrade());
    }

    @Test
    @DisplayName("없는 회원의 요금제를 변경하면 UserNotFoundException을 던진다")
    void 관리자_요금제변경_없는회원() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.changePlanByAdmin(9L, 999L, 50000));
    }

    @Test
    @DisplayName("검색어는 소문자 %값% 패턴이 되고, 비어 있으면 조건 없음(null)이다")
    void 검색어_패턴() {
        assertEquals("%user%", UserService.likePattern("  USER "));
        assertNull(UserService.likePattern(null));
        assertNull(UserService.likePattern("   "));
    }

    @Test
    @DisplayName("검색어 안의 % _ 와 이스케이프 문자는 글자 그대로 찾도록 이스케이프한다")
    void 검색어_와일드카드_이스케이프() {
        assertEquals("%a!%b%", UserService.likePattern("a%b"));
        assertEquals("%a!_b%", UserService.likePattern("a_b"));
        assertEquals("%a!!b%", UserService.likePattern("a!b"));
    }
}
