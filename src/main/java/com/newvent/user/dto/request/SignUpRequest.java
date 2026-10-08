package com.newvent.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 요금제는 받지 않는다 - 가입자가 정한 요금제로 등급이 올라가면 안 되므로 서버 기본 요금제로 시작하고, 이후 변경은 관리자만 한다. 요청에 plan 이 들어 있어도 무시된다.
public record SignUpRequest(
        @NotBlank @Size(max = 50) String loginId,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Email @Size(max = 100) String email,
        @Size(max = 20) String phone) {}
