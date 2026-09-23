package com.newvent.user.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

// 부분 수정용 — 필드가 없으면(null) 해당 값은 그대로 둔다.
public record UpdateUserRequest(
        @Size(max = 50) String name, @Email @Size(max = 100) String email, @Size(max = 20) String phone) {}
