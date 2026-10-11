package com.newvent.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import com.newvent.auth.dto.AuthUser;
import com.newvent.auth.jwt.JwtProvider;
import com.newvent.common.config.SecurityConfig;

@WebMvcTest(MembershipGradeController.class)
@Import({SecurityConfig.class, JwtProvider.class})
class MembershipGradeApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("로그인한 사용자는 등급 종류를 낮은 등급부터 이름·설명과 함께 본다")
    void 등급종류_조회() throws Exception {
        mockMvc.perform(get("/api/membership-grades")
                        .header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.user(7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].grade").value("NORMAL"))
                .andExpect(jsonPath("$.data[0].name").value("일반"))
                .andExpect(jsonPath("$.data[1].grade").value("EXCELLENT"))
                .andExpect(jsonPath("$.data[2].grade").value("BEST"))
                .andExpect(jsonPath("$.data[2].description").isNotEmpty());
    }

    @Test
    @DisplayName("산정 기준(점수·금액·기간)은 응답에 담기지 않는다")
    void 산정기준은_노출되지_않는다() throws Exception {
        mockMvc.perform(get("/api/membership-grades")
                        .header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.user(7L))))
                .andExpect(jsonPath("$.data[0].score").doesNotExist())
                .andExpect(jsonPath("$.data[0].minScore").doesNotExist())
                .andExpect(jsonPath("$.data[0].criteria").doesNotExist());
    }

    @Test
    @DisplayName("토큰이 없으면 401이다")
    void 토큰없음_401() throws Exception {
        mockMvc.perform(get("/api/membership-grades")).andExpect(status().isUnauthorized());
    }

    private String bearer(AuthUser principal) {
        return "Bearer " + jwtProvider.issue(principal).value();
    }
}
