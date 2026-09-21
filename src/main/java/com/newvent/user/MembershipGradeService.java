package com.newvent.user;

import java.time.LocalDate;

/**
 * membership_grade 재계산 시점은 3곳으로 제한
 *   1. 회원가입 시
 *   2. 요금제 변경 시
 *   3. 로그인 시 
 *
 * 가입기간 경과만으로 자동 승급시키는 배치·스케줄러는 두지 않기로 함
 * 실제 통신사 환경을 가정해 "등급은 별도 고객·과금 시스템에서 산정된다"고 보고, 본 프로젝트는 위 3개 시점에만 계산
 *
 * 단점 : 오래 로그인하지 않은 계정은 실제(재계산했다면 나왔을) 등급보다 낮은 값으로 남아있울 수 있음.
 * 요금제가 그대로면 가입기간 점수는 시간이 지날수록만 오르므로, 저장된 값이 실제보다 높게 잘못될 일은 없다 — 다음 로그인 때 최신화 됨
 */
public final class MembershipGradeService {

    private MembershipGradeService() {
    }

    // 회원가입 시 최초 등급을 계산
    public static MembershipGrade onSignUp(int plan, LocalDate joinedAt) {
        return onSignUp(plan, joinedAt, LocalDate.now());
    }

    // 요금제 변경 시 즉시 재계산
    public static MembershipGrade onPlanChanged(int newPlan, LocalDate joinedAt) {
        return onPlanChanged(newPlan, joinedAt, LocalDate.now());
    }

    // 로그인 시 재계산 — JWT에 넣을 등급이 최신인지 여기서 보장
    public static MembershipGrade onLogin(int plan, LocalDate joinedAt) {
        return onLogin(plan, joinedAt, LocalDate.now());
    }

    // 아래 3개는 테스트에서 기준일을 고정해 결정적으로 검증하기 위한 오버로드

    static MembershipGrade onSignUp(int plan, LocalDate joinedAt, LocalDate today) {
        return recalculate(plan, joinedAt, today);
    }

    static MembershipGrade onPlanChanged(int newPlan, LocalDate joinedAt, LocalDate today) {
        return recalculate(newPlan, joinedAt, today);
    }

    static MembershipGrade onLogin(int plan, LocalDate joinedAt, LocalDate today) {
        return recalculate(plan, joinedAt, today);
    }

    private static MembershipGrade recalculate(int plan, LocalDate joinedAt, LocalDate today) {
        return MembershipGradeCalculator.calculate(plan, joinedAt, today);
    }
}
