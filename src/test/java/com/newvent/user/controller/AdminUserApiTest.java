package com.newvent.user.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.newvent.auth.dto.AuthUser;
import com.newvent.auth.jwt.JwtProvider;
import com.newvent.common.config.SecurityConfig;
import com.newvent.common.response.PageResponse;
import com.newvent.user.domain.MembershipGrade;
import com.newvent.user.domain.User;
import com.newvent.user.dto.response.AdminUserSummaryResponse;
import com.newvent.user.exception.UserNotFoundException;
import com.newvent.user.service.UserService;

// 실제 인가 규칙(/api/admin/** 는 ADMIN)으로 검증하려고 SecurityConfig 를 직접 import 하고, 토큰은 JwtProvider 로 진짜를 발급한다.
@WebMvcTest(AdminUserController.class)
@Import({SecurityConfig.class, JwtProvider.class})
class AdminUserApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("관리자는 회원 목록을 검색어·등급·페이지로 조회한다")
    void 목록_조회() throws Exception {
        AdminUserSummaryResponse item = new AdminUserSummaryResponse(
                7L, "user07", "테스트유저07", "user07@test.com", 25000, MembershipGrade.EXCELLENT,
                LocalDate.of(2019, 3, 1));
        when(userService.searchUsers("user", MembershipGrade.EXCELLENT, 1, 5))
                .thenReturn(PageResponse.of(List.of(item), 1, 5, 6));

        mockMvc.perform(get("/api/admin/users")
                        .param("keyword", "user")
                        .param("grade", "EXCELLENT")
                        .param("page", "1")
                        .param("size", "5")
                        .header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.admin(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].loginId").value("user07"))
                .andExpect(jsonPath("$.data.content[0].plan").value(25000))
                .andExpect(jsonPath("$.data.content[0].membershipGrade").value("EXCELLENT"))
                .andExpect(jsonPath("$.data.content[0].joinedAt").value("2019-03-01"))
                .andExpect(jsonPath("$.data.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.totalElements").value(6))
                .andExpect(jsonPath("$.data.totalPages").value(2));
    }

    @Test
    @DisplayName("조건 없이 부르면 첫 페이지 10건이 기본이다")
    void 목록_기본값() throws Exception {
        when(userService.searchUsers(isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(PageResponse.of(List.of(), 0, 10, 0));

        mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.admin(1L))))
                .andExpect(status().isOk());
        verify(userService).searchUsers(null, null, 0, 10);
    }

    @Test
    @DisplayName("size 가 50 을 넘거나 page 가 음수이거나 등급 값이 틀리면 400이다")
    void 목록_잘못된_파라미터_400() throws Exception {
        String token = bearer(AuthUser.admin(1L));

        mockMvc.perform(get("/api/admin/users").param("size", "51").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/users").param("page", "-1").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/users").param("grade", "VIP").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("관리자는 회원 상세를 본다 - 연락처와 가입·수정 시각이 있고 비밀번호 해시는 없다")
    void 상세_조회() throws Exception {
        User user = user("user01", 50000, MembershipGrade.NORMAL);
        when(userService.getById(7L)).thenReturn(user);

        mockMvc.perform(get("/api/admin/users/7").header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.admin(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginId").value("user01"))
                .andExpect(jsonPath("$.data.phone").value("010-0000-0000"))
                .andExpect(jsonPath("$.data.joinedAt").value("2024-03-15"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @DisplayName("없는 회원 상세는 404이다")
    void 상세_없는회원_404() throws Exception {
        when(userService.getById(anyLong())).thenThrow(new UserNotFoundException());

        mockMvc.perform(get("/api/admin/users/999").header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.admin(1L))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("관리자가 요금제를 바꾸면 재계산된 등급이 담긴 상세를 돌려주고, 누가 바꿨는지 서비스에 전달한다")
    void 요금제변경_성공() throws Exception {
        User user = user("user01", 70000, MembershipGrade.EXCELLENT);
        when(userService.changePlanByAdmin(eq(1L), eq(7L), eq(70000))).thenReturn(user);

        mockMvc.perform(patch("/api/admin/users/7/plan")
                        .header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.admin(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plan":70000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plan").value(70000))
                .andExpect(jsonPath("$.data.membershipGrade").value("EXCELLENT"));
        verify(userService).changePlanByAdmin(1L, 7L, 70000);
    }

    @Test
    @DisplayName("요금제가 0 이하이거나 없으면 400이다")
    void 요금제변경_유효성실패() throws Exception {
        String token = bearer(AuthUser.admin(1L));

        mockMvc.perform(patch("/api/admin/users/7/plan").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plan":-5}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/admin/users/7/plan").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plan":0}
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("없는 회원의 요금제 변경은 404이다")
    void 요금제변경_없는회원_404() throws Exception {
        when(userService.changePlanByAdmin(anyLong(), anyLong(), anyInt())).thenThrow(new UserNotFoundException());

        mockMvc.perform(patch("/api/admin/users/999/plan")
                        .header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.admin(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plan":50000}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("일반 사용자 토큰으로는 관리자 회원 API 를 부를 수 없다 (403)")
    void 사용자토큰_403() throws Exception {
        String token = bearer(AuthUser.user(7L));

        mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users/7").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/users/7/plan").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plan":100000}
                                """))
                .andExpect(status().isForbidden());
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("토큰이 없으면 관리자 회원 API 는 401이다")
    void 토큰없음_401() throws Exception {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/users/7")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/admin/users/7/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plan":100000}
                                """))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(userService);
    }

    private User user(String loginId, int plan, MembershipGrade grade) {
        User user = new User(loginId, "hash", "이름", loginId + "@test.com", "010-0000-0000", plan, grade);
        ReflectionTestUtils.setField(user, "createdAt", OffsetDateTime.parse("2024-03-15T10:00:00+09:00"));
        ReflectionTestUtils.setField(user, "updatedAt", OffsetDateTime.parse("2024-04-01T10:00:00+09:00"));
        return user;
    }

    private String bearer(AuthUser principal) {
        return "Bearer " + jwtProvider.issue(principal).value();
    }
}
