package com.newvent.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

class MemberServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final MemberService memberService = new MemberService(userRepository, passwordEncoder);

    @Test
    @DisplayName("회원가입 시 비밀번호를 해싱하고 가입 당일 기준으로 등급을 계산해 저장한다")
    void 회원가입_비밀번호해싱과_등급계산() {
        when(userRepository.existsByLoginId("user01")).thenReturn(false);
        when(userRepository.existsByEmail("user01@test.com")).thenReturn(false);
        when(passwordEncoder.encode("raw-password")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User saved = memberService.signUp(
                "user01", "raw-password", "테스트", "user01@test.com", "010-0000-0000", 70000);

        assertEquals("hashed-password", saved.getPasswordHash());
        // 70,000원(프리미엄=3점) + 가입 당일(0개월=1점) = 4점 → EXCELLENT
        assertEquals(MembershipGrade.EXCELLENT, saved.getMembershipGrade());
        verify(userRepository).save(saved);
    }

    @Test
    @DisplayName("이미 사용 중인 로그인 ID면 DuplicateUserException을 던진다")
    void 회원가입_로그인ID중복() {
        when(userRepository.existsByLoginId("dup")).thenReturn(true);

        assertThrows(
                DuplicateUserException.class,
                () -> memberService.signUp("dup", "pw", "이름", "a@test.com", null, 50000));
    }

    @Test
    @DisplayName("이미 사용 중인 이메일이면 DuplicateUserException을 던진다")
    void 회원가입_이메일중복() {
        when(userRepository.existsByLoginId("new")).thenReturn(false);
        when(userRepository.existsByEmail("dup@test.com")).thenReturn(true);

        assertThrows(
                DuplicateUserException.class,
                () -> memberService.signUp("new", "pw", "이름", "dup@test.com", null, 50000));
    }

    @Test
    @DisplayName("존재하는 id를 조회하면 해당 회원을 반환한다")
    void 조회_성공() {
        User user = new User("user01", "hash", "이름", "user01@test.com", null, 50000, MembershipGrade.NORMAL);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User found = memberService.getById(1L);

        assertEquals(user, found);
    }

    @Test
    @DisplayName("존재하지 않는 id를 조회하면 UserNotFoundException을 던진다")
    void 조회_없는ID() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> memberService.getById(999L));
    }

    @Test
    @DisplayName("정보 수정 시 null로 넘어온 필드는 바꾸지 않는다")
    void 정보수정_부분수정() {
        User user =
                new User("user01", "hash", "기존이름", "old@test.com", "010-1111-1111", 50000, MembershipGrade.NORMAL);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User updated = memberService.updateProfile(1L, null, null, "010-2222-2222");

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
                DuplicateUserException.class, () -> memberService.updateProfile(1L, null, "taken@test.com", null));
    }

    @Test
    @DisplayName("요금제를 변경하면 가입일 기준으로 등급을 재계산한다")
    void 요금제변경_등급재계산() {
        User user = new User("user01", "hash", "이름", "a@test.com", null, 25000, MembershipGrade.NORMAL);
        // @CreationTimestamp는 실제 영속화 시점에만 채워지므로, mock 단위테스트에서는 직접 주입한다.
        ReflectionTestUtils.setField(user, "createdAt", OffsetDateTime.now().minusYears(6));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User updated = memberService.changePlan(1L, 70000);

        // 70,000원(프리미엄=3점) + 5년 이상(3점) = 6점 → BEST
        assertEquals(70000, updated.getPlan());
        assertEquals(MembershipGrade.BEST, updated.getMembershipGrade());
    }
}
