package com.newvent.user.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
        @NotBlank @Size(max = 50) String loginId,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Email @Size(max = 100) String email,
        @Size(max = 20) String phone,
        @Positive int plan) {}
