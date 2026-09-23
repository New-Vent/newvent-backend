package com.newvent.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.newvent.user.domain.MembershipGrade;

class MembershipGradeCalculatorTest {

    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 9, 21);

    @ParameterizedTest(name = "{0}: plan={1}, joinedAt={2} → {3}")
    @MethodSource("matrixAndBoundaryFixtures")
    @DisplayName("더미데이터 매트릭스·경계값 18건이 저장된 grade와 일치한다")
    void 등급이_더미데이터와_일치한다(String label, int plan, LocalDate joinedAt, MembershipGrade expected) {
        MembershipGrade actual = MembershipGradeCalculator.calculate(plan, joinedAt, REFERENCE_DATE);

        assertEquals(expected, actual, label + " 등급이 다릅니다");
    }

    @Test
    @DisplayName("가입일의 '일자'는 결과에 영향을 주지 않는다 (월 단위 판정)")
    void 가입일_일자는_무시된다() {
        // boundary_join_1y_exact(2025-09-01) vs boundary_join_1y_exact_eom(2025-09-30)
        // — db/V2 주석: "일자가 달라도 같은 달이면 결과가 같아야 함"
        MembershipGrade early = MembershipGradeCalculator.calculate(50_000, LocalDate.of(2025, 9, 1), REFERENCE_DATE);
        MembershipGrade endOfMonth =
                MembershipGradeCalculator.calculate(50_000, LocalDate.of(2025, 9, 30), REFERENCE_DATE);

        assertEquals(early, endOfMonth);
        assertEquals(MembershipGrade.EXCELLENT, early);
    }

    @Test
    @DisplayName("plan이 0 이하면 예외를 던진다 (DB의 CHECK (plan > 0)과 동일한 전제)")
    void plan이_0이하면_예외() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MembershipGradeCalculator.calculate(0, LocalDate.of(2023, 1, 1), REFERENCE_DATE));
        assertThrows(
                IllegalArgumentException.class,
                () -> MembershipGradeCalculator.calculate(-1, LocalDate.of(2023, 1, 1), REFERENCE_DATE));
    }

    @Test
    @DisplayName("joinedAt이 referenceDate보다 미래면 예외를 던진다 (음수 monthsElapsed로 정상 등급이 나오면 안 됨)")
    void joinedAt이_미래면_예외() {
        LocalDate oneDayAfter = REFERENCE_DATE.plusDays(1);
        LocalDate oneYearAfter = REFERENCE_DATE.plusYears(1);

        assertThrows(
                IllegalArgumentException.class,
                () -> MembershipGradeCalculator.calculate(50_000, oneDayAfter, REFERENCE_DATE));
        assertThrows(
                IllegalArgumentException.class,
                () -> MembershipGradeCalculator.calculate(50_000, oneYearAfter, REFERENCE_DATE));
    }

    static Stream<Arguments> matrixAndBoundaryFixtures() {
        return Stream.of(
                // 매트릭스 9칸 (요금제 × 가입기간 전 조합)
                Arguments.of("user01", 25_000, LocalDate.of(2026, 1, 10), MembershipGrade.NORMAL),
                Arguments.of("user02", 50_000, LocalDate.of(2026, 2, 15), MembershipGrade.NORMAL),
                Arguments.of("user03", 70_000, LocalDate.of(2026, 1, 20), MembershipGrade.EXCELLENT),
                Arguments.of("user04", 25_000, LocalDate.of(2023, 5, 1), MembershipGrade.NORMAL),
                Arguments.of("user05", 50_000, LocalDate.of(2023, 6, 10), MembershipGrade.EXCELLENT),
                Arguments.of("user06", 70_000, LocalDate.of(2023, 7, 5), MembershipGrade.EXCELLENT),
                Arguments.of("user07", 25_000, LocalDate.of(2019, 3, 1), MembershipGrade.EXCELLENT),
                Arguments.of("user08", 50_000, LocalDate.of(2018, 11, 20), MembershipGrade.EXCELLENT),
                Arguments.of("user09", 70_000, LocalDate.of(2017, 8, 15), MembershipGrade.BEST),

                // 요금제 축 경계값 (가입기간은 2023-01-01 고정 = 1~5년, 2점)
                Arguments.of("boundary_fee_light_max", 39_999, LocalDate.of(2023, 1, 1), MembershipGrade.NORMAL),
                Arguments.of("boundary_fee_standard_min", 40_000, LocalDate.of(2023, 1, 1), MembershipGrade.EXCELLENT),
                Arguments.of("boundary_fee_standard_max", 59_999, LocalDate.of(2023, 1, 1), MembershipGrade.EXCELLENT),
                Arguments.of("boundary_fee_premium_min", 60_000, LocalDate.of(2023, 1, 1), MembershipGrade.EXCELLENT),

                // 가입기간 축 경계값 (요금제는 50,000원 고정 = 스탠다드, 2점)
                Arguments.of("boundary_join_1y_under", 50_000, LocalDate.of(2025, 10, 1), MembershipGrade.NORMAL),
                Arguments.of("boundary_join_1y_exact", 50_000, LocalDate.of(2025, 9, 1), MembershipGrade.EXCELLENT),
                Arguments.of(
                        "boundary_join_1y_exact_eom", 50_000, LocalDate.of(2025, 9, 30), MembershipGrade.EXCELLENT),
                Arguments.of("boundary_join_5y_under", 50_000, LocalDate.of(2021, 10, 1), MembershipGrade.EXCELLENT),
                Arguments.of("boundary_join_5y_exact", 50_000, LocalDate.of(2021, 9, 1), MembershipGrade.EXCELLENT));
    }
}
