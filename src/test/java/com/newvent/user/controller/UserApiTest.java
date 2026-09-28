package com.newvent.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.newvent.user.domain.MembershipGrade;
import com.newvent.user.domain.User;
import com.newvent.user.exception.DuplicateUserException;
import com.newvent.user.exception.UserNotFoundException;
import com.newvent.user.exception.code.UserErrorCode;
import com.newvent.user.service.UserService;

@WebMvcTest(UserController.class)
class UserApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("회원가입 성공 시 201과 생성된 회원 정보를 반환한다")
    void 회원가입_성공() throws Exception {
        User user = new User(
                "user01", "hash", "이름", "user01@test.com", "010-0000-0000", 70000, MembershipGrade.EXCELLENT);
        when(userService.signUp(anyString(), anyString(), anyString(), anyString(), any(), anyInt()))
                .thenReturn(user);

        mockMvc.perform(post("/api/public/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"loginId":"user01","password":"password123","name":"이름",
                                 "email":"user01@test.com","phone":"010-0000-0000","plan":70000}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.loginId").value("user01"))
                .andExpect(jsonPath("$.data.membershipGrade").value("EXCELLENT"));
    }

    @Test
    @DisplayName("plan이 0 이하면 400을 반환한다 (서비스까지 못 감)")
    void 회원가입_plan_유효성실패() throws Exception {
        mockMvc.perform(post("/api/public/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"loginId":"user01","password":"password123","name":"이름",
                                 "email":"user01@test.com","plan":0}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비밀번호가 8자 미만이면 400을 반환한다")
    void 회원가입_비밀번호_유효성실패() throws Exception {
        mockMvc.perform(post("/api/public/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"loginId":"user01","password":"short","name":"이름",
                                 "email":"user01@test.com","plan":50000}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("중복 가입 시도 시 서비스가 던진 예외를 409로 변환한다")
    void 회원가입_중복ID_409() throws Exception {
        when(userService.signUp(anyString(), anyString(), anyString(), anyString(), any(), anyInt()))
                .thenThrow(new DuplicateUserException(UserErrorCode.DUPLICATE_LOGIN_ID));

        mockMvc.perform(post("/api/public/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"loginId":"user01","password":"password123","name":"이름",
                                 "email":"user01@test.com","plan":50000}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("존재하는 회원을 조회하면 200과 회원 정보를 반환한다")
    void 조회_성공() throws Exception {
        User user = new User("user01", "hash", "이름", "user01@test.com", "010-0000-0000", 50000,
                MembershipGrade.NORMAL);
        when(userService.getById(1L)).thenReturn(user);

        mockMvc.perform(get("/api/public/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginId").value("user01"))
                .andExpect(jsonPath("$.data.membershipGrade").value("NORMAL"));
    }

    @Test
    @DisplayName("존재하지 않는 회원을 조회하면 404를 반환한다")
    void 조회_없는회원_404() throws Exception {
        when(userService.getById(anyLong())).thenThrow(new UserNotFoundException());

        mockMvc.perform(get("/api/public/users/999")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("정보 수정 성공 시 200과 갱신된 정보를 반환한다")
    void 정보수정_성공() throws Exception {
        User user = new User(
                "user01", "hash", "새이름", "user01@test.com", "010-9999-9999", 50000, MembershipGrade.NORMAL);
        when(userService.updateProfile(anyLong(), any(), any(), any())).thenReturn(user);

        mockMvc.perform(patch("/api/public/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"010-9999-9999"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("010-9999-9999"));
    }

    @Test
    @DisplayName("정보 수정 시 공백 문자열을 보내면 400을 반환한다 (필드 생략과는 구분)")
    void 정보수정_공백값_유효성실패() throws Exception {
        mockMvc.perform(patch("/api/public/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"   "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("요금제 변경 성공 시 재계산된 등급을 반환한다")
    void 요금제변경_성공() throws Exception {
        User user = new User("user01", "hash", "이름", "user01@test.com", null, 70000, MembershipGrade.EXCELLENT);
        when(userService.changePlan(anyLong(), anyInt())).thenReturn(user);

        mockMvc.perform(patch("/api/public/users/1/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plan":70000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.membershipGrade").value("EXCELLENT"));
    }

    @Test
    @DisplayName("요금제에 음수를 넣으면 400을 반환한다")
    void 요금제변경_유효성실패() throws Exception {
        mockMvc.perform(patch("/api/public/users/1/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plan":-5}
                                """))
                .andExpect(status().isBadRequest());
    }
}
