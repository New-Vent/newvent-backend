package com.newvent.user.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.newvent.user.domain.MembershipGrade;

/**
 * 멤버십 등급 산정 로직.
 *   순수 계산 함수라 스스로는 "언제 호출돼야 하는지" 모른다 — 그건 MembershipGradeService가 정한다.
 *   이 클래스를 조회(GET)할 때마다 다시 호출하지 않는다. 대신 회원가입/요금제변경/로그인, 이 3곳 에서만 호출해 결과를 users.membership_grade에 저장하고, 그 외 조회는 저장된 값을 신뢰한다
 *   (MembershipGradeService 참고). 등급이 바뀌어도 이미 발급된 로그인 토큰에는 즉시 반영되지 않는다 - Access Token이 30분~1시간으로 짧으므로 그 정도 지연은 허용하기로 함.
 */
public final class MembershipGradeCalculator {

    private static final int LIGHT_MAX_FEE = 39_999;
    private static final int STANDARD_MAX_FEE = 59_999;

    private static final int ONE_YEAR_MONTHS = 12;
    private static final int FIVE_YEARS_MONTHS = 60;

    private MembershipGradeCalculator() {
    }

    public static MembershipGrade calculate(int plan, LocalDate joinedAt, LocalDate referenceDate) {
        if (plan <= 0) {
            throw new IllegalArgumentException("plan은 0보다 커야 합니다: " + plan);
        }
        if (joinedAt.isAfter(referenceDate)) {
            throw new IllegalArgumentException(
                    "joinedAt은 referenceDate보다 미래일 수 없습니다: joinedAt=" + joinedAt
                            + ", referenceDate=" + referenceDate);
        }
        int score = feeScore(plan) + periodScore(monthsElapsed(joinedAt, referenceDate));
        return gradeOf(score);
    }

    private static int feeScore(int plan) {
        if (plan <= LIGHT_MAX_FEE) {
            return 1;
        }
        if (plan <= STANDARD_MAX_FEE) {
            return 2;
        }
        return 3;
    }

    private static int periodScore(int monthsElapsed) {
        if (monthsElapsed < ONE_YEAR_MONTHS) {
            return 1;
        }
        if (monthsElapsed < FIVE_YEARS_MONTHS) {
            return 2;
        }
        return 3;
    }

    // 연·월 차이만 계산하고 일자는 버린다 (월 단위 판정)
    private static int monthsElapsed(LocalDate joinedAt, LocalDate referenceDate) {
        LocalDate joinedMonth = joinedAt.withDayOfMonth(1);
        LocalDate referenceMonth = referenceDate.withDayOfMonth(1);
        return (int) ChronoUnit.MONTHS.between(joinedMonth, referenceMonth);
    }

    private static MembershipGrade gradeOf(int score) {
        if (score <= 3) {
            return MembershipGrade.NORMAL;
        }
        if (score <= 5) {
            return MembershipGrade.EXCELLENT;
        }
        return MembershipGrade.BEST;
    }
}
