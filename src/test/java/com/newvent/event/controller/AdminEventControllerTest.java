package com.newvent.event.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.newvent.auth.dto.AuthUser;
import com.newvent.auth.jwt.JwtProvider;
import com.newvent.common.config.SecurityConfig;
import com.newvent.common.exception.handler.GlobalExceptionHandler;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.dto.request.EventCreateRequest;
import com.newvent.event.dto.response.EventDetailResponse;
import com.newvent.event.dto.response.EventSummaryResponse;
import com.newvent.event.dto.response.PageResponse;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.service.EventService;
import com.newvent.user.domain.MembershipGrade;

// @WebMvcTest 는 SecurityConfig 를 스캔하지 않는다 — 안 넣으면 Spring Security 기본 설정(전부 인증 + CSRF)이 걸린다.
// 실제 인가 규칙(/api/admin/** 는 ADMIN)으로 검증하려고 직접 import 하고, 관리자로 요청한다.
@WebMvcTest(AdminEventController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtProvider.class})
@WithMockUser(roles = "ADMIN")
class AdminEventControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtProvider jwtProvider;

    @MockitoBean
    EventService eventService;

    @Test
    @DisplayName("관리자 목록 API 는 ApiResponse 로 감싼다")
    void 이벤트_목록_조회에_성공한다() throws Exception {
        EventSummaryResponse row = new EventSummaryResponse(
                1L, "신규 가입 데이터 쿠폰 3GB", EventStatus.PUBLISHED,
                OffsetDateTime.parse("2026-09-16T00:00:00+09:00"),
                OffsetDateTime.parse("2026-10-15T23:59:59+09:00"),
                OffsetDateTime.parse("2026-09-16T10:20:00+09:00"),
                "signup", null, MembershipGrade.NORMAL, false);
        given(eventService.findAdminEvents(null, null, null, null, 0, 10))
                .willReturn(PageResponse.of(List.of(row), 0, 10, 1));

        mockMvc.perform(get("/api/admin/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].name").value("신규 가입 데이터 쿠폰 3GB"))
                .andExpect(jsonPath("$.data.content[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("관리자 상세 API 는 HTML 을 포함한다")
    void 이벤트_상세_조회에_성공한다() throws Exception {
        EventDetailResponse detail = new EventDetailResponse(
                3L, "지금 긁으면 바로 당첨", EventStatus.PUBLISHED,
                OffsetDateTime.parse("2026-09-16T00:00:00+09:00"),
                OffsetDateTime.parse("2026-09-18T23:59:59+09:00"),
                OffsetDateTime.parse("2026-09-15T09:10:00+09:00"),
                "instant", null, MembershipGrade.NORMAL,
                "<h1>지금 긁으면 바로 당첨</h1>", true);
        given(eventService.findAdminEvent(3L)).willReturn(detail);

        mockMvc.perform(get("/api/admin/events/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(3))
                .andExpect(jsonPath("$.data.closingSoon").value(true))
                .andExpect(jsonPath("$.data.completedHtml").value("<h1>지금 긁으면 바로 당첨</h1>"));
    }

    @Test
    @DisplayName("없는 이벤트는 404 와 EVENT404-0 을 반환한다")
    void 존재하지_않는_이벤트_조회시_404를_반환한다() throws Exception {
        given(eventService.findAdminEvent(999L))
                .willThrow(new EventException(EventErrorCode.EVENT_NOT_FOUND));

        mockMvc.perform(get("/api/admin/events/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT404-0"))
                .andExpect(jsonPath("$.message").value("이벤트를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("생성 API 는 201 과 DRAFT 를 반환한다")
    void 이벤트_생성에_성공한다() throws Exception {
        EventDetailResponse created = new EventDetailResponse(
                100L, "테스트 이벤트", EventStatus.DRAFT,
                OffsetDateTime.parse("2026-10-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-10-15T23:59:59+09:00"),
                OffsetDateTime.parse("2026-09-16T01:00:00+09:00"),
                "sports_cheer", null, MembershipGrade.BEST,
                null, false);
        given(eventService.create(eq(1L), any(EventCreateRequest.class))).willReturn(created);

        mockMvc.perform(post("/api/admin/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.issue(AuthUser.admin(1L)).value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "테스트 이벤트",
                                  "startAt": "2026-10-01T00:00:00+09:00",
                                  "endAt": "2026-10-15T23:59:59+09:00",
                                  "templateKey": "sports_cheer",
                                  "grade": "BEST"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.grade").value("BEST"))
                .andExpect(jsonPath("$.data.completedHtml").doesNotExist());
    }

    @Test
    @DisplayName("조회 시작일이 종료일보다 늦으면 400 과 EVENT400-1 을 반환한다")
    void 조회기간이_역전되면_400을_반환한다() throws Exception {
        given(eventService.findAdminEvents(
                        any(), any(), any(OffsetDateTime.class), any(OffsetDateTime.class),
                        anyInt(), anyInt()))
                .willThrow(new EventException(EventErrorCode.INVALID_SEARCH_PERIOD));

        mockMvc.perform(get("/api/admin/events")
                        .param("periodFrom", "2026-09-30T00:00:00+09:00")
                        .param("periodTo", "2026-09-01T00:00:00+09:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EVENT400-1"));
    }

    @Test
    @DisplayName("이벤트명 누락은 400 과 COMMON400-0 을 반환한다")
    void 이벤트명_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/admin/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "startAt": "2026-10-01T00:00:00+09:00",
                                  "endAt": "2026-10-15T23:59:59+09:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-0"));
    }

    @Test
    @DisplayName("수정 골격은 501 을 반환한다")
    void 이벤트_수정_골격은_501이다() throws Exception {
        mockMvc.perform(patch("/api/admin/events/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "이름만 변경" }
                                """))
                .andExpect(status().isNotImplemented());
    }

    @Test
    @DisplayName("수정 요청의 이벤트명이 공백만 있으면 400 과 COMMON400-0 을 반환한다")
    void 이벤트_수정_이름이_공백뿐이면_400을_반환한다() throws Exception {
        mockMvc.perform(patch("/api/admin/events/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "   " }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-0"));
    }

    @Test
    @DisplayName("수정 요청의 이벤트명이 빈 문자열이면 400 과 COMMON400-0 을 반환한다")
    void 이벤트_수정_이름이_빈_문자열이면_400을_반환한다() throws Exception {
        mockMvc.perform(patch("/api/admin/events/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-0"));
    }

    @Test
    @DisplayName("수정 요청에 이벤트명이 없으면 검증을 통과한다")
    void 이벤트_수정_이름이_없으면_검증을_통과한다() throws Exception {
        mockMvc.perform(patch("/api/admin/events/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "endAt": "2026-10-20T23:59:59+09:00" }
                                """))
                .andExpect(status().isNotImplemented());
    }

    @Test
    @DisplayName("삭제 골격은 501 을 반환한다")
    void 이벤트_삭제_골격은_501이다() throws Exception {
        mockMvc.perform(delete("/api/admin/events/1"))
                .andExpect(status().isNotImplemented());
    }

    @Test
    @DisplayName("상태 변경 골격은 501 을 반환한다")
    void 이벤트_상태변경_골격은_501이다() throws Exception {
        mockMvc.perform(patch("/api/admin/events/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "PUBLISHED" }
                                """))
                .andExpect(status().isNotImplemented());
    }

    @Test
    @DisplayName("게시 골격은 501 을 반환한다")
    void 이벤트_게시_골격은_501이다() throws Exception {
        mockMvc.perform(post("/api/admin/events/1/publish"))
                .andExpect(status().isNotImplemented());
    }
}
