package com.newvent.user;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 멤버십 등급 산정 로직.
 *   DB의 membership_grade 컬럼은 이 계산 결과를 캐시해둔 값일 뿐이다. 조회 API는 항상 이 계산기를
 *   다시 돌려서 응답을 만들고, 저장된 컬럼 값을 그대로 믿지 않는다.
 *   등급이 바뀌어도 이미 발급된 로그인 토큰에는 즉시 반영되지 않는다 - Access Token이 30분~1시간의 지연은 허용하기로 함.
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
