package com.newvent.user.dto.response;

import java.time.LocalDate;

import com.newvent.user.domain.MembershipGrade;
import com.newvent.user.domain.User;

// 관리자 목록용 - 비밀번호 해시는 어떤 응답에도 x
public record AdminUserSummaryResponse(
        Long id,
        String loginId,
        String name,
        String email,
        int plan,
        MembershipGrade membershipGrade,
        LocalDate joinedAt) {

    public static AdminUserSummaryResponse from(User user) {
        return new AdminUserSummaryResponse(
                user.getId(), user.getLoginId(), user.getName(), user.getEmail(), user.getPlan(),
                user.getMembershipGrade(), user.joinedAt());
    }
}
