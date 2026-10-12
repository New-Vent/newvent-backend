package com.newvent.user.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.newvent.common.domain.BaseTimeEntity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

//   plan은 요금제 "금액"(원 단위), membership_grade는 캐시값 - 회원가입·요금제변경·로그인
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, length = 50, unique = true)
    private String loginId;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 100, unique = true)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false)
    private Integer plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "membership_grade", nullable = false, length = 20)
    private MembershipGrade membershipGrade;

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

    // 아직 저장 전이라 createdAt 이 없으면 null
    public LocalDate joinedAt() {
        return getCreatedAt() == null ? null : getCreatedAt().toLocalDate();
    }

    public void changePlan(int newPlan, MembershipGrade recalculatedGrade) {
        if (newPlan <= 0) {
            throw new IllegalArgumentException("plan은 0보다 커야 합니다: " + newPlan);
        }
        this.plan = newPlan;
        this.membershipGrade = recalculatedGrade;
    }

    public void refreshMembershipGrade(MembershipGrade recalculatedGrade) {
        this.membershipGrade = recalculatedGrade;
    }

    // 부분 수정 — null인 필드는 그대로 둔다 (PATCH 의미)
    public void updateProfile(String name, String email, String phone) {
        if (name != null) {
            this.name = name;
        }
        if (email != null) {
            this.email = email;
        }
        if (phone != null) {
            this.phone = phone;
        }
    }
}
