package com.newvent.user.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.newvent.user.domain.MembershipGrade;
import com.newvent.user.domain.User;

import io.swagger.v3.oas.annotations.media.Schema;

// 관리자 상세용 - 목록 항목에 연락처와 가입·수정 시각 추가. 비밀번호 해시는 넣지 않는다.
public record AdminUserDetailResponse(
        Long id,
        String loginId,
        String name,
        String email,
        String phone,
        @Schema(description = "월 요금(원)", example = "35000")
        int plan,
        @Schema(description = "멤버십 등급. 요금제와 가입 기간으로 서버가 계산한다.")
        MembershipGrade membershipGrade,
        @Schema(description = "가입일")
        LocalDate joinedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static AdminUserDetailResponse from(User user) {
        return new AdminUserDetailResponse(
                user.getId(), user.getLoginId(), user.getName(), user.getEmail(), user.getPhone(), user.getPlan(),
                user.getMembershipGrade(), user.joinedAt(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
