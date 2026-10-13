package com.newvent.user.dto.response;

import java.time.LocalDate;

import com.newvent.user.domain.MembershipGrade;
import com.newvent.user.domain.User;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserResponse(
        Long id,
        String loginId,
        String name,
        String email,
        String phone,
        @Schema(description = "월 요금(원). 가입 때는 서버 기본값으로 시작하고 관리자만 바꾼다.", example = "35000")
        int plan,
        @Schema(description = "멤버십 등급. 요금제와 가입 기간으로 서버가 계산한다.")
        MembershipGrade membershipGrade,
        @Schema(description = "가입일")
        LocalDate joinedAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(), user.getLoginId(), user.getName(), user.getEmail(), user.getPhone(), user.getPlan(),
                user.getMembershipGrade(), user.joinedAt());
    }
}
