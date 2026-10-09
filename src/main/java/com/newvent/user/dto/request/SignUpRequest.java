package com.newvent.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

public record SignUpRequest(
        // 영문(대소문자)·숫자·밑줄만, 4~50자. 가입 때만 검사하므로 이미 있는 계정의 로그인에는 영향이 없다.
        @Schema(
                        description = "영문 대소문자·숫자·밑줄(_)만, 4~50자. 입력한 대소문자 그대로 저장한다. "
                                + "대소문자만 다른 아이디(user01 / USER01)가 이미 있으면 409 (USER409-0). "
                                + "로그인은 저장된 대소문자 그대로 입력해야 한다.",
                        example = "User_01")
                @NotBlank
                @Pattern(regexp = "^[A-Za-z0-9_]{4,50}$", message = "아이디는 영문, 숫자, 밑줄(_)만 사용해 4~50자로 입력해주세요")
                String loginId,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 50) String name,
        @Schema(description = "대소문자만 다른 이메일(a@x.com / A@x.com)이 이미 있으면 409 (USER409-1)")
                @NotBlank
                @Email
                @Size(max = 100)
                String email,
        @Size(max = 20) String phone,
        @Positive int plan) {}
