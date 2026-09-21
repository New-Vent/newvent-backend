package com.newvent.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MembershipGradeServiceTest {

    @Test
    @DisplayName("세 트리거 모두 MembershipGradeCalculator와 같은 결과를 낸다")
    void 트리거_셋_다_계산기와_동일하다() {
        int plan = 70_000; // 프리미엄
        LocalDate joinedAt = LocalDate.of(2023, 7, 5);
        LocalDate today = LocalDate.of(2026, 9, 21);

        MembershipGrade expected = MembershipGradeCalculator.calculate(plan, joinedAt, today);

        assertEquals(expected, MembershipGradeService.onSignUp(plan, joinedAt, today));
        assertEquals(expected, MembershipGradeService.onPlanChanged(plan, joinedAt, today));
        assertEquals(expected, MembershipGradeService.onLogin(plan, joinedAt, today));
    }

    @Test
    @DisplayName("가입 시점엔 낮은 등급이어도, 시간이 지난 뒤 로그인하면 오른 등급으로 재계산된다")
    void 로그인_시점에_등급이_올라간다() {
        int plan = 50_000; // 스탠다드 — 요금제는 안 바꿈
        LocalDate joinedAt = LocalDate.of(2025, 9, 1);

        // 가입 직후: 0개월 경과 → 1년 미만(1점) + 스탠다드(2점) = 3점 → 일반
        MembershipGrade atSignUp = MembershipGradeService.onSignUp(plan, joinedAt, joinedAt);
        assertEquals(MembershipGrade.NORMAL, atSignUp);

        // 1년 뒤 로그인: 12개월 경과 → 1~5년(2점) + 스탠다드(2점) = 4점 → 우수
        // 실제 서비스에서는 이 값을 users.membership_grade에 다시 저장한다.
        // 로그인이 없었다면 이 재계산 자체가 안 일어나므로 저장된 값은 가입 시점 그대로 남는다 그게 MembershipGradeService의 javadoc에 적어둔 "허용한 지연"이다.
        MembershipGrade oneYearLater = MembershipGradeService.onLogin(plan, joinedAt, LocalDate.of(2026, 9, 1));
        assertEquals(MembershipGrade.EXCELLENT, oneYearLater);
    }
}
