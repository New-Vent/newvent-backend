package com.newvent.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

// 부분 수정용 — 필드가 없으면(null) 해당 값은 그대로 둔다. 값이 전달되면 공백만으로는 안 된다.
public record UpdateUserRequest(
        @Schema(description = "이름. 보내지 않으면 그대로 둔다.")
        @Pattern(regexp = ".*\\S.*", message = "공백일 수 없습니다") @Size(max = 50) String name,
        @Schema(description = "이메일. 보내지 않으면 그대로 둔다. 다른 회원이 쓰고 있으면 409 (USER409-1)")
        @Pattern(regexp = ".*\\S.*", message = "공백일 수 없습니다") @Email @Size(max = 100) String email,
        @Schema(description = "전화번호. 보내지 않으면 그대로 둔다.")
        @Pattern(regexp = ".*\\S.*", message = "공백일 수 없습니다") @Size(max = 20) String phone) {}
