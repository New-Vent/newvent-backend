package com.newvent.user.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserTest {

    @Test
    @DisplayName("plan이 0 이하면 생성 시 예외를 던진다")
    void 생성_plan0이하_예외() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new User("id", "hash", "이름", "a@test.com", null, 0, MembershipGrade.NORMAL));
        assertThrows(
                IllegalArgumentException.class,
                () -> new User("id", "hash", "이름", "a@test.com", null, -1, MembershipGrade.NORMAL));
    }

    @Test
    @DisplayName("membershipGrade가 null이면 생성 시 예외를 던진다")
    void 생성_등급null_예외() {
        assertThrows(
                IllegalArgumentException.class, () -> new User("id", "hash", "이름", "a@test.com", null, 50000, null));
    }

    @Test
    @DisplayName("요금제를 0 이하로 변경하려 하면 예외를 던진다")
    void changePlan_0이하_예외() {
        User user = new User("id", "hash", "이름", "a@test.com", null, 50000, MembershipGrade.NORMAL);

        assertThrows(IllegalArgumentException.class, () -> user.changePlan(0, MembershipGrade.EXCELLENT));
    }

    @Test
    @DisplayName("요금제를 정상 변경하면 plan과 등급이 함께 갱신된다")
    void changePlan_정상() {
        User user = new User("id", "hash", "이름", "a@test.com", null, 50000, MembershipGrade.NORMAL);

        user.changePlan(70000, MembershipGrade.EXCELLENT);

        assertEquals(70000, user.getPlan());
        assertEquals(MembershipGrade.EXCELLENT, user.getMembershipGrade());
    }

    @Test
    @DisplayName("로그인 시에는 등급만 새로 반영한다 (plan은 그대로)")
    void refreshMembershipGrade_등급만_갱신() {
        User user = new User("id", "hash", "이름", "a@test.com", null, 50000, MembershipGrade.NORMAL);

        user.refreshMembershipGrade(MembershipGrade.BEST);

        assertEquals(50000, user.getPlan());
        assertEquals(MembershipGrade.BEST, user.getMembershipGrade());
    }

    @Test
    @DisplayName("정보 수정 시 null로 넘긴 필드는 바뀌지 않는다")
    void updateProfile_null은_유지() {
        User user =
                new User("id", "hash", "원래이름", "old@test.com", "010-0000-0000", 50000, MembershipGrade.NORMAL);

        user.updateProfile(null, "new@test.com", null);

        assertEquals("원래이름", user.getName());
        assertEquals("new@test.com", user.getEmail());
        assertEquals("010-0000-0000", user.getPhone());
    }

    @Test
    @DisplayName("joinedAt은 created_at을 날짜(LocalDate)로 변환한다")
    void joinedAt_변환() {
        User user = new User("id", "hash", "이름", "a@test.com", null, 50000, MembershipGrade.NORMAL);
        // @CreationTimestamp는 실제 영속화 시점에만 채워지므로, 단위테스트에서는 직접 주입한다.
        ReflectionTestUtils.setField(user, "createdAt", OffsetDateTime.parse("2023-05-10T12:34:56+09:00"));

        assertEquals(LocalDate.of(2023, 5, 10), user.joinedAt());
    }
}
