package com.newvent.user.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.common.response.ApiResponse;
import com.newvent.user.dto.response.MembershipGradeResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

// 어떤 등급이 있고 무엇을 뜻하는지만 표시. 산정 기준(점수표·금액·기간)은 x
@Tag(name = "멤버십 등급", description = "어떤 등급이 있고 무엇을 뜻하는지 안내한다. 로그인한 사용자·관리자 모두 볼 수 있다.")
@RestController
public class MembershipGradeController {

    @Operation(
            summary = "등급 종류 안내",
            description = "낮은 등급부터 이름·설명을 내려준다. 산정 기준(점수·금액·기간)은 내려주지 않는다.")
    @GetMapping("/api/membership-grades")
    public ApiResponse<List<MembershipGradeResponse>> list() {
        return ApiResponse.success(MembershipGradeResponse.all());
    }
}
