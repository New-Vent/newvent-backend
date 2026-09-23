package com.newvent.admin.domain;

import jakarta.persistence.*;

import com.newvent.common.domain.BaseTimeEntity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "admins")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Admin extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, length = 50, unique = true)
    private String loginId;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** 인증 연동 전 인메모리·생성 API 용 스텁. */
    public static Admin systemStub() {
        Admin admin = new Admin();
        admin.id = 1L;
        admin.loginId = "system";
        admin.passwordHash = "n/a";
        admin.name = "system";
        admin.active = true;
        return admin;
    }
}
