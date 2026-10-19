package com.newvent.admin.dto.response;

import com.newvent.admin.domain.Admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "로그인한 관리자 본인 정보")
public record AdminProfileResponse(
    @Schema(description = "관리자 ID", example = "1")
    Long id,
    @Schema(description = "로그인 ID", example = "admin01")
    String loginId,
    @Schema(description = "관리자 이름", example = "뉴벤트")
    String name
) {
    public static AdminProfileResponse from(Admin admin) {
        return AdminProfileResponse.builder()
            .id(admin.getId())
            .loginId(admin.getLoginId())
            .name(admin.getName())
            .build();
    }
}
