package com.newvent.user.dto.response;

import java.time.LocalDate;

import com.newvent.user.domain.MembershipGrade;
import com.newvent.user.domain.User;

import io.swagger.v3.oas.annotations.media.Schema;

// 관리자 목록용 - 비밀번호 해시는 어떤 응답에도 x
public record AdminUserSummaryResponse(
        Long id,
        String loginId,
        String name,
        String email,
        @Schema(description = "월 요금(원)", example = "35000")
        int plan,
        @Schema(description = "멤버십 등급. 요금제와 가입 기간으로 서버가 계산한다.")
        MembershipGrade membershipGrade,
        @Schema(description = "가입일")
        LocalDate joinedAt) {

    public static AdminUserSummaryResponse from(User user) {
        return new AdminUserSummaryResponse(
                user.getId(), user.getLoginId(), user.getName(), user.getEmail(), user.getPlan(),
                user.getMembershipGrade(), user.joinedAt());
    }
}
