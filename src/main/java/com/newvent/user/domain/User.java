package com.newvent.user.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * users 테이블 매핑 (ERD v2에서 확정된 스키마, PR #11의 V1__init_schema.sql과 일치).
 *   plan은 요금제 "금액"(원 단위), membership_grade는 캐시값이다 — 회원가입·요금제변경·로그인
 *   시점에 MembershipGradeService로 재계산한 결과만 반영하고, 그 외 조회에서는 이 값을
 *   그대로 신뢰한다 (재계산은 이 엔티티가 아니라 호출하는 쪽 책임).
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, unique = true, length = 50)
    private String loginId;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false)
    private int plan;

    @Convert(converter = MembershipGradeConverter.class)
    @Column(name = "membership_grade", nullable = false, length = 20)
    private MembershipGrade membershipGrade;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected User() {
        // JPA
    }

    public User(String loginId, String passwordHash, String name, String email, String phone,
                int plan, MembershipGrade membershipGrade) {
        if (plan <= 0) {
            throw new IllegalArgumentException("plan은 0보다 커야 합니다: " + plan);
        }
        if (membershipGrade == null) {
            throw new IllegalArgumentException("membershipGrade는 null일 수 없습니다");
        }
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.plan = plan;
        this.membershipGrade = membershipGrade;
    }

    public Long id() {
        return id;
    }

    public String loginId() {
        return loginId;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String name() {
        return name;
    }

    public String email() {
        return email;
    }

    public String phone() {
        return phone;
    }

    public int plan() {
        return plan;
    }

    public MembershipGrade membershipGrade() {
        return membershipGrade;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }

    /** created_at은 가입일 겸용이다 — MembershipGradeCalculator가 받는 LocalDate로 변환해 넘긴다 */
    public LocalDate joinedAt() {
        return createdAt.toLocalDate();
    }

    /** 요금제 변경 — 새 plan과, 호출자가 MembershipGradeService.onPlanChanged로 재계산한 등급을 함께 반영 */
    public void changePlan(int newPlan, MembershipGrade recalculatedGrade) {
        if (newPlan <= 0) {
            throw new IllegalArgumentException("plan은 0보다 커야 합니다: " + newPlan);
        }
        this.plan = newPlan;
        this.membershipGrade = recalculatedGrade;
    }

    /** 로그인 시점 재계산 결과 반영 — MembershipGradeService.onLogin 결과를 저장 */
    public void refreshMembershipGrade(MembershipGrade recalculatedGrade) {
        this.membershipGrade = recalculatedGrade;
    }
}
