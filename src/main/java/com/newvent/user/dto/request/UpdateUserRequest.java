package com.newvent.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 부분 수정용 — 필드가 없으면(null) 해당 값은 그대로 둔다. 값이 전달되면 공백만으로는 안 된다.
public record UpdateUserRequest(
        @Pattern(regexp = ".*\\S.*", message = "공백일 수 없습니다") @Size(max = 50) String name,
        @Pattern(regexp = ".*\\S.*", message = "공백일 수 없습니다") @Email @Size(max = 100) String email,
        @Pattern(regexp = ".*\\S.*", message = "공백일 수 없습니다") @Size(max = 20) String phone) {}
