package com.newvent.notification.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.newvent.auth.dto.AuthUser;
import com.newvent.auth.jwt.JwtProvider;
import com.newvent.common.config.SecurityConfig;
import com.newvent.common.exception.handler.GlobalExceptionHandler;
import com.newvent.event.dto.response.PageResponse;
import com.newvent.notification.dto.response.AdminNotificationResponse;
import com.newvent.notification.exception.AdminNotificationErrorCode;
import com.newvent.notification.exception.AdminNotificationException;
import com.newvent.notification.service.AdminNotificationService;

@WebMvcTest(AdminNotificationController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtProvider.class})
@WithMockUser(roles = "ADMIN")
class AdminNotificationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtProvider jwtProvider;

    @MockitoBean
    AdminNotificationService notificationService;

    @Test
    @DisplayName("목록 API는 ApiResponse로 감싼다")
    void 알림_목록_조회에_성공한다() throws Exception {
        AdminNotificationResponse row = new AdminNotificationResponse(
                1L, 10L, "테스트 이벤트", "STARTED", "'테스트 이벤트' 이벤트가 시작됐습니다.",
                false, OffsetDateTime.parse("2026-10-01T10:00:00+09:00"));
        given(notificationService.getNotifications(eq(5L), isNull(), eq(0), eq(10)))
                .willReturn(PageResponse.of(List.of(row), 0, 10, 1));

        mockMvc.perform(get("/api/admin/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.issue(AuthUser.admin(5L)).value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].eventName").value("테스트 이벤트"))
                .andExpect(jsonPath("$.data.content[0].type").value("STARTED"));
    }

    @Test
    @DisplayName("eventId를 주면 그 이벤트의 알림만 조회한다")
    void 이벤트별_알림_목록_조회에_성공한다() throws Exception {
        AdminNotificationResponse row = new AdminNotificationResponse(
                1L, 10L, "테스트 이벤트", "STARTED", "'테스트 이벤트' 이벤트가 시작됐습니다.",
                false, OffsetDateTime.parse("2026-10-01T10:00:00+09:00"));
        given(notificationService.getNotifications(eq(5L), eq(10L), eq(0), eq(10)))
                .willReturn(PageResponse.of(List.of(row), 0, 10, 1));

        mockMvc.perform(get("/api/admin/notifications")
                        .param("eventId", "10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.issue(AuthUser.admin(5L)).value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].eventId").value(10));
    }

    @Test
    @DisplayName("읽음 처리 API는 200을 반환한다")
    void 알림_읽음_처리에_성공한다() throws Exception {
        mockMvc.perform(patch("/api/admin/notifications/1/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.issue(AuthUser.admin(5L)).value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).markRead(5L, 1L);
    }

    @Test
    @DisplayName("존재하지 않는 알림의 읽음 처리는 404와 NOTI404-0을 반환한다")
    void 없는_알림_읽음처리는_404를_반환한다() throws Exception {
        willThrow(new AdminNotificationException(AdminNotificationErrorCode.NOTIFICATION_NOT_FOUND))
                .given(notificationService).markRead(5L, 999L);

        mockMvc.perform(patch("/api/admin/notifications/999/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.issue(AuthUser.admin(5L)).value()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTI404-0"));
    }
}
