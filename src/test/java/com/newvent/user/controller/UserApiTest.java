package com.newvent.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

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
import com.newvent.user.domain.MembershipGrade;
import com.newvent.user.domain.User;
import com.newvent.user.exception.DuplicateUserException;
import com.newvent.user.exception.UserNotFoundException;
import com.newvent.user.exception.code.UserErrorCode;
import com.newvent.user.service.UserService;

// @WebMvcTest 는 SecurityConfig 를 스캔하지 않는다 — 안 넣으면 Spring Security 기본 설정(전부 인증 + CSRF)이 걸린다.
// 실제 인가 규칙(signup 공개, /api/users/** 는 USER)으로 검증하려고 직접 import 하고, 토큰은 JwtProvider 로 진짜를 발급한다.
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtProvider.class})
class UserApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("회원가입 성공 시 201과 생성된 회원 정보를 반환한다")
    void 회원가입_성공() throws Exception {
        User user = new User(
                "user01", "hash", "이름", "user01@test.com", "010-0000-0000", 35000, MembershipGrade.NORMAL);
        when(userService.signUp(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(user);

        mockMvc.perform(post("/api/public/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"loginId":"user01","password":"password123","name":"이름",
                                 "email":"user01@test.com","phone":"010-0000-0000"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.loginId").value("user01"))
                .andExpect(jsonPath("$.data.membershipGrade").value("NORMAL"));
    }

    @Test
    @DisplayName("가입 요청에 plan 을 넣어도 무시되고 서비스에는 요금제가 전달되지 않는다")
    void 회원가입_plan은_무시된다() throws Exception {
        User user = new User(
                "user01", "hash", "이름", "user01@test.com", "010-0000-0000", 35000, MembershipGrade.NORMAL);
        when(userService.signUp(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(user);

        mockMvc.perform(post("/api/public/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"loginId":"user01","password":"password123","name":"이름",
                                 "email":"user01@test.com","phone":"010-0000-0000","plan":100000}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.plan").value(35000))
                .andExpect(jsonPath("$.data.membershipGrade").value("NORMAL"));
        verify(userService).signUp("user01", "password123", "이름", "user01@test.com", "010-0000-0000");
    }

    @Test
    @DisplayName("비밀번호가 8자 미만이면 400을 반환한다")
    void 회원가입_비밀번호_유효성실패() throws Exception {
        mockMvc.perform(post("/api/public/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"loginId":"user01","password":"short","name":"이름",
                                 "email":"user01@test.com"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("중복 가입 시도 시 서비스가 던진 예외를 409로 변환한다")
    void 회원가입_중복ID_409() throws Exception {
        when(userService.signUp(anyString(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new DuplicateUserException(UserErrorCode.DUPLICATE_LOGIN_ID));

        mockMvc.perform(post("/api/public/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"loginId":"user01","password":"password123","name":"이름",
                                 "email":"user01@test.com"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("내 정보 조회는 경로가 아니라 토큰의 id 로 회원을 찾고, 요금제·등급·가입일을 돌려준다")
    void 내정보_조회_성공() throws Exception {
        User user = new User("user01", "hash", "이름", "user01@test.com", "010-0000-0000", 50000,
                MembershipGrade.NORMAL);
        ReflectionTestUtils.setField(user, "createdAt", OffsetDateTime.parse("2024-03-15T10:00:00+09:00"));
        when(userService.getById(7L)).thenReturn(user);

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.user(7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginId").value("user01"))
                .andExpect(jsonPath("$.data.plan").value(50000))
                .andExpect(jsonPath("$.data.membershipGrade").value("NORMAL"))
                .andExpect(jsonPath("$.data.joinedAt").value("2024-03-15"));
        verify(userService).getById(7L);
    }

    @Test
    @DisplayName("토큰의 회원이 없으면 404를 반환한다")
    void 내정보_조회_없는회원_404() throws Exception {
        when(userService.getById(anyLong())).thenThrow(new UserNotFoundException());

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.user(999L))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("토큰 없이 내 정보 API 를 부르면 401 이다")
    void 내정보_토큰없음_401() throws Exception {
        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/users/me/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plan":70000}
                                """))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("관리자 토큰의 id 는 admins 의 id 라 내 정보 API 에서 403 이다")
    void 내정보_관리자토큰_403() throws Exception {
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.admin(1L))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("예전 공개 경로(/api/public/users/{id})로는 더 이상 조회·수정할 수 없다")
    void 예전_공개경로는_막힌다() throws Exception {
        mockMvc.perform(get("/api/public/users/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/public/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"010-9999-9999"}
                                """))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/public/users/1/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plan":70000}
                                """))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("정보 수정 성공 시 200과 갱신된 정보를 반환한다")
    void 정보수정_성공() throws Exception {
        User user = new User(
                "user01", "hash", "새이름", "user01@test.com", "010-9999-9999", 50000, MembershipGrade.NORMAL);
        when(userService.updateProfile(anyLong(), any(), any(), any())).thenReturn(user);

        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.user(7L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"010-9999-9999"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("010-9999-9999"));
        verify(userService).updateProfile(7L, null, null, "010-9999-9999");
    }

    @Test
    @DisplayName("정보 수정 시 공백 문자열을 보내면 400을 반환한다 (필드 생략과는 구분)")
    void 정보수정_공백값_유효성실패() throws Exception {
        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.user(7L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"   "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("사용자는 요금제를 바꿀 수 없다 - 변경 API 가 없어 성공하지 못하고 서비스도 호출되지 않는다")
    void 사용자_요금제변경_불가() throws Exception {
        int statusCode = mockMvc.perform(patch("/api/users/me/plan")
                        .header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.user(7L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plan":100000}
                                """))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(statusCode).isGreaterThanOrEqualTo(400);
        verifyNoInteractions(userService);
    }

    private String bearer(AuthUser principal) {
        return "Bearer " + jwtProvider.issue(principal).value();
    }
}
