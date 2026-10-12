package com.newvent.generation.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.newvent.auth.dto.AuthUser;
import com.newvent.auth.jwt.JwtProvider;
import com.newvent.common.config.SecurityConfig;
import com.newvent.common.response.PageResponse;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.generation.domain.ChatMessageStatus;
import com.newvent.generation.domain.ChatRole;
import com.newvent.generation.dto.ChatMessageResponse;
import com.newvent.generation.service.ChatHistoryService;

// 실제 인가 규칙(/api/admin/** 는 ADMIN)으로 검증하려고 SecurityConfig 를 직접 import 하고, 토큰은 JwtProvider 로 진짜를 발급한다.
@WebMvcTest(AdminChatMessageController.class)
@Import({SecurityConfig.class, JwtProvider.class})
class AdminChatMessageApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private ChatHistoryService history;

    @Test
    @DisplayName("관리자는 이벤트의 대화를 받고, 토큰의 관리자 id 가 서비스에 전달된다")
    void 조회_성공() throws Exception {
        ChatMessageResponse request = new ChatMessageResponse(
                1L, ChatRole.ADMIN, "제목을 바꿔줘", "hero", ChatMessageStatus.COMPLETED,
                OffsetDateTime.parse("2026-10-11T10:00:00+09:00"), 3);
        ChatMessageResponse reply = new ChatMessageResponse(
                2L, ChatRole.ASSISTANT, "수정을 반영했습니다.", null, ChatMessageStatus.COMPLETED,
                OffsetDateTime.parse("2026-10-11T10:00:05+09:00"), null);
        when(history.list(eq(5L), eq(9L), eq(0), eq(50)))
                .thenReturn(PageResponse.of(List.of(request, reply), 0, 50, 2));

        mockMvc.perform(get("/api/admin/events/5/chat-messages")
                        .header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.admin(9L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].role").value("ADMIN"))
                .andExpect(jsonPath("$.data.content[0].content").value("제목을 바꿔줘"))
                .andExpect(jsonPath("$.data.content[0].targetBlock").value("hero"))
                .andExpect(jsonPath("$.data.content[0].versionNo").value(3))
                .andExpect(jsonPath("$.data.content[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data.content[1].versionNo").doesNotExist())
                .andExpect(jsonPath("$.data.totalElements").value(2));
        verify(history).list(5L, 9L, 0, 50);
    }

    @Test
    @DisplayName("page · size 를 쿼리로 받는다")
    void 페이지_파라미터() throws Exception {
        when(history.list(anyLong(), anyLong(), anyInt(), anyInt()))
                .thenReturn(PageResponse.of(List.of(), 2, 20, 0));

        mockMvc.perform(get("/api/admin/events/5/chat-messages")
                        .param("page", "2")
                        .param("size", "20")
                        .header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.admin(9L))))
                .andExpect(status().isOk());
        verify(history).list(5L, 9L, 2, 20);
    }

    @Test
    @DisplayName("size 가 100 을 넘거나 page 가 음수면 400 이다")
    void 잘못된_파라미터_400() throws Exception {
        String token = bearer(AuthUser.admin(9L));

        mockMvc.perform(get("/api/admin/events/5/chat-messages").param("size", "101")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/events/5/chat-messages").param("page", "-1")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(history);
    }

    @Test
    @DisplayName("이벤트를 만든 관리자가 아니면 서비스가 던진 예외가 404 로 나간다")
    void 소유자가_아니면_404() throws Exception {
        when(history.list(anyLong(), anyLong(), anyInt(), anyInt()))
                .thenThrow(new EventException(EventErrorCode.EVENT_NOT_ACCESSIBLE));

        mockMvc.perform(get("/api/admin/events/5/chat-messages")
                        .header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.admin(9L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT404-2"));
    }

    @Test
    @DisplayName("일반 사용자 토큰으로는 부를 수 없다 (403)")
    void 사용자_토큰_403() throws Exception {
        mockMvc.perform(get("/api/admin/events/5/chat-messages")
                        .header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.user(7L))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(history);
    }

    @Test
    @DisplayName("토큰이 없으면 401 이다")
    void 토큰_없음_401() throws Exception {
        mockMvc.perform(get("/api/admin/events/5/chat-messages")).andExpect(status().isUnauthorized());
        verifyNoInteractions(history);
    }

    private String bearer(AuthUser principal) {
        return "Bearer " + jwtProvider.issue(principal).value();
    }
}
