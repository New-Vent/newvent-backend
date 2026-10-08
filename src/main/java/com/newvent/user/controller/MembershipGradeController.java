package com.newvent.user.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.common.response.ApiResponse;
import com.newvent.user.dto.response.MembershipGradeResponse;

// 어떤 등급이 있고 무엇을 뜻하는지만 표시. 산정 기준(점수표·금액·기간)은 x
@RestController
public class MembershipGradeController {

    @GetMapping("/api/membership-grades")
    public ApiResponse<List<MembershipGradeResponse>> list() {
        return ApiResponse.success(MembershipGradeResponse.all());
    }
}
