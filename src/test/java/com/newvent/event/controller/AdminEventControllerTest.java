package com.newvent.event.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.newvent.auth.dto.AuthUser;
import com.newvent.auth.jwt.JwtProvider;
import com.newvent.common.config.SecurityConfig;
import com.newvent.common.exception.handler.GlobalExceptionHandler;
import com.newvent.common.response.PageResponse;
import com.newvent.event.domain.EventProgress;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.dto.request.EventCreateRequest;
import com.newvent.event.dto.request.EventUpdateRequest;
import com.newvent.event.dto.response.EventCountsResponse;
import com.newvent.event.dto.response.EventDetailResponse;
import com.newvent.event.dto.response.EventSummaryResponse;
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

    private RequestPostProcessor adminPrincipal() {
        return authentication(new UsernamePasswordAuthenticationToken(
                AuthUser.admin(1L), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

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
                "signup", "<section data-block=\"hero\"><h1>쿠폰</h1></section>", MembershipGrade.NORMAL, false, 2, 3);
        given(eventService.findAdminEvents(null, null, null, null, null, 0, 10))
                .willReturn(PageResponse.of(List.of(row), 0, 10, 1));

        mockMvc.perform(get("/api/admin/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].name").value("신규 가입 데이터 쿠폰 3GB"))
                .andExpect(jsonPath("$.data.content[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.content[0].publishedVersionNo").value(2))
                .andExpect(jsonPath("$.data.content[0].latestVersionNo").value(3))
                .andExpect(jsonPath("$.data.content[0].thumbnailHtml").value(containsString("data-block=\"hero\"")))
                .andExpect(jsonPath("$.data.content[0].thumbnailUrl").doesNotExist())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("목록 API 는 검색어·상태·진행상태를 서비스에 넘긴다")
    void 이벤트_목록_필터를_전달한다() throws Exception {
        given(eventService.findAdminEvents(
                        "42", EventStatus.PUBLISHED, EventProgress.ONGOING, null, null, 0, 10))
                .willReturn(PageResponse.of(List.of(), 0, 10, 0));

        mockMvc.perform(get("/api/admin/events")
                        .param("name", "42")
                        .param("status", "PUBLISHED")
                        .param("progress", "ONGOING"))
                .andExpect(status().isOk());

        verify(eventService).findAdminEvents(
                "42", EventStatus.PUBLISHED, EventProgress.ONGOING, null, null, 0, 10);
    }

    @Test
    @DisplayName("없는 진행상태 값은 400 과 COMMON400-0 을 반환한다")
    void 잘못된_진행상태는_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/admin/events").param("progress", "LIVE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-0"));
    }

    @Test
    @DisplayName("관리자 상세 API 는 HTML 을 포함한다")
    void 이벤트_상세_조회에_성공한다() throws Exception {
        EventDetailResponse detail = new EventDetailResponse(
                3L, "지금 긁으면 바로 당첨", EventStatus.PUBLISHED,
                OffsetDateTime.parse("2026-09-16T00:00:00+09:00"),
                OffsetDateTime.parse("2026-09-18T23:59:59+09:00"),
                OffsetDateTime.parse("2026-09-15T09:10:00+09:00"),
                "instant", MembershipGrade.NORMAL,
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
    @DisplayName("카운트 API 는 상태별 건수를 반환한다")
    void 이벤트_카운트_조회에_성공한다() throws Exception {
        given(eventService.findEventCounts())
                .willReturn(new EventCountsResponse(10, 3, 2, 5));

        mockMvc.perform(get("/api/admin/events/counts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(10))
                .andExpect(jsonPath("$.data.published").value(3))
                .andExpect(jsonPath("$.data.draft").value(2))
                .andExpect(jsonPath("$.data.ended").value(5));
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
                "sports_cheer", MembershipGrade.BEST,
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
                        any(), any(), any(), any(OffsetDateTime.class), any(OffsetDateTime.class),
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
    @DisplayName("수정 API 는 200 과 수정된 이벤트를 반환한다")
    void 이벤트_수정에_성공한다() throws Exception {
        EventDetailResponse updated = new EventDetailResponse(
                2L, "이름만 변경", EventStatus.DRAFT,
                OffsetDateTime.parse("2026-07-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-07-31T23:59:59+09:00"),
                OffsetDateTime.parse("2026-09-16T01:00:00+09:00"),
                null, MembershipGrade.EXCELLENT,
                null, false);
        given(eventService.update(eq(1L), eq(2L), any(EventUpdateRequest.class))).willReturn(updated);

        mockMvc.perform(patch("/api/admin/events/2").with(adminPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "이름만 변경" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.name").value("이름만 변경"))
                .andExpect(jsonPath("$.data.grade").value("EXCELLENT"));
    }

    @Test
    @DisplayName("종료된 이벤트 수정은 409 와 EVENT409-1 을 반환한다")
    void 종료된_이벤트_수정은_409를_반환한다() throws Exception {
        given(eventService.update(eq(1L), eq(6L), any(EventUpdateRequest.class)))
                .willThrow(new EventException(EventErrorCode.EVENT_ENDED_NOT_EDITABLE));

        mockMvc.perform(patch("/api/admin/events/6").with(adminPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "이름 변경" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT409-1"));
    }

    @Test
    @DisplayName("게시 중인 이벤트의 템플릿 변경은 409 와 EVENT409-4 를 반환한다")
    void 게시중_템플릿_변경은_409를_반환한다() throws Exception {
        given(eventService.update(eq(1L), eq(4L), any(EventUpdateRequest.class)))
                .willThrow(new EventException(EventErrorCode.PUBLISHED_EVENT_TEMPLATE_NOT_EDITABLE));

        mockMvc.perform(patch("/api/admin/events/4").with(adminPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "templateKey": "sports_cheer" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT409-4"));
    }

    @Test
    @DisplayName("수정 후 기간이 역전되면 400 과 EVENT400-0 을 반환한다")
    void 수정_기간이_역전되면_400을_반환한다() throws Exception {
        given(eventService.update(eq(1L), eq(2L), any(EventUpdateRequest.class)))
                .willThrow(new EventException(EventErrorCode.INVALID_PERIOD));

        mockMvc.perform(patch("/api/admin/events/2").with(adminPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "endAt": "2026-06-30T00:00:00+09:00" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EVENT400-0"));
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
        mockMvc.perform(patch("/api/admin/events/1").with(adminPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "endAt": "2026-10-20T23:59:59+09:00" }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("삭제 API 는 200 을 반환한다")
    void 이벤트_삭제에_성공한다() throws Exception {
        mockMvc.perform(delete("/api/admin/events/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(eventService).delete(1L);
    }

    @Test
    @DisplayName("게시 중인 이벤트 삭제는 409 와 EVENT409-2 를 반환한다")
    void 게시중인_이벤트_삭제는_409를_반환한다() throws Exception {
        willThrow(new EventException(EventErrorCode.PUBLISHED_EVENT_DELETE_FORBIDDEN))
                .given(eventService).delete(1L);

        mockMvc.perform(delete("/api/admin/events/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT409-2"));
    }

    @Test
    @DisplayName("없는 이벤트 삭제는 404 와 EVENT404-0 을 반환한다")
    void 없는_이벤트_삭제는_404를_반환한다() throws Exception {
        willThrow(new EventException(EventErrorCode.EVENT_NOT_FOUND))
                .given(eventService).delete(999L);

        mockMvc.perform(delete("/api/admin/events/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT404-0"));
    }

    @Test
    @DisplayName("생성 중인 이벤트 삭제는 409 와 EVENT409-3 을 반환한다")
    void 생성중인_이벤트_삭제는_409를_반환한다() throws Exception {
        willThrow(new EventException(EventErrorCode.EVENT_GENERATING_DELETE_FORBIDDEN))
                .given(eventService).delete(1L);

        mockMvc.perform(delete("/api/admin/events/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT409-3"));
    }

    @Test
    @DisplayName("영구 삭제 API 는 200 을 반환한다")
    void 이벤트_영구삭제에_성공한다() throws Exception {
        mockMvc.perform(delete("/api/admin/events/99/permanent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(eventService).hardDelete(99L);
    }

    @Test
    @DisplayName("삭제되지 않은 이벤트 영구삭제는 404 와 EVENT404-0 을 반환한다")
    void 삭제되지_않은_이벤트_영구삭제는_404를_반환한다() throws Exception {
        willThrow(new EventException(EventErrorCode.EVENT_NOT_FOUND))
                .given(eventService).hardDelete(1L);

        mockMvc.perform(delete("/api/admin/events/1/permanent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT404-0"));
    }

    @Test
    @DisplayName("참여 기록이 있는 이벤트 영구삭제는 409 와 EVENT409-9 를 반환한다")
    void 참여_기록이_있는_이벤트_영구삭제는_409를_반환한다() throws Exception {
        willThrow(new EventException(EventErrorCode.PARTICIPATED_EVENT_PERMANENT_DELETE_FORBIDDEN))
                .given(eventService).hardDelete(1L);

        mockMvc.perform(delete("/api/admin/events/1/permanent"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT409-9"));
    }

    @Test
    @DisplayName("휴지통 목록 API 는 ApiResponse 로 감싼다")
    void 휴지통_목록_조회에_성공한다() throws Exception {
        EventSummaryResponse row = new EventSummaryResponse(
                99L, "삭제된 이벤트", EventStatus.DRAFT,
                OffsetDateTime.parse("2026-01-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-01-10T23:59:59+09:00"),
                OffsetDateTime.parse("2026-01-11T00:00:00+09:00"),
                null, "<section data-block=\"hero\"><h1>쿠폰</h1></section>", MembershipGrade.NORMAL, false, null, null);
        given(eventService.findDeletedEvents(0, 10))
                .willReturn(PageResponse.of(List.of(row), 0, 10, 1));

        mockMvc.perform(get("/api/admin/events/trash"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(99))
                .andExpect(jsonPath("$.data.content[0].name").value("삭제된 이벤트"))
                .andExpect(jsonPath("$.data.content[0].thumbnailHtml").value(containsString("data-block=\"hero\"")));
    }

    @Test
    @DisplayName("복구 API 는 복구된 이벤트 상세를 반환한다")
    void 이벤트_복구에_성공한다() throws Exception {
        EventDetailResponse restored = new EventDetailResponse(
                99L, "삭제된 이벤트", EventStatus.DRAFT,
                OffsetDateTime.parse("2026-01-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-01-10T23:59:59+09:00"),
                OffsetDateTime.parse("2026-01-11T00:00:00+09:00"),
                null, MembershipGrade.NORMAL, null, false);
        given(eventService.restore(99L)).willReturn(restored);

        mockMvc.perform(post("/api/admin/events/99/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(99));
    }

    @Test
    @DisplayName("삭제되지 않은 이벤트 복구는 404 와 EVENT404-0 을 반환한다")
    void 삭제되지_않은_이벤트_복구는_404를_반환한다() throws Exception {
        given(eventService.restore(1L)).willThrow(new EventException(EventErrorCode.EVENT_NOT_FOUND));

        mockMvc.perform(post("/api/admin/events/1/restore"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT404-0"));
    }

    @Test
    @DisplayName("종료 API 는 종료된 이벤트 상세를 ApiResponse 로 감싼다")
    void 이벤트_종료에_성공한다() throws Exception {
        EventDetailResponse ended = new EventDetailResponse(
                3L, "가을 멤버십 더블 혜택", EventStatus.ENDED,
                OffsetDateTime.parse("2026-09-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-09-30T23:59:59+09:00"),
                OffsetDateTime.parse("2026-09-30T13:00:00+09:00"),
                "member_appreciation", MembershipGrade.NORMAL,
                "<h1>가을 멤버십 더블 혜택</h1>", false);
        given(eventService.changeStatus(3L, EventStatus.ENDED)).willReturn(ended);

        mockMvc.perform(patch("/api/admin/events/3/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "ENDED" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(3))
                .andExpect(jsonPath("$.data.status").value("ENDED"));
    }

    @Test
    @DisplayName("상태 없이 종료를 요청하면 400 과 COMMON400-0 을 반환한다")
    void 상태_누락은_400을_반환한다() throws Exception {
        mockMvc.perform(patch("/api/admin/events/3/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-0"));
    }

    @Test
    @DisplayName("게시 중이 아닌 이벤트 종료는 409 와 EVENT409-6 을 반환한다")
    void 게시중이_아닌_이벤트_종료는_409를_반환한다() throws Exception {
        given(eventService.changeStatus(2L, EventStatus.ENDED))
                .willThrow(new EventException(EventErrorCode.EVENT_NOT_ENDABLE));

        mockMvc.perform(patch("/api/admin/events/2/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "ENDED" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT409-6"));
    }

    @Test
    @DisplayName("종료가 아닌 상태를 요청하면 400 과 EVENT400-2 를 반환한다")
    void 종료가_아닌_상태_요청은_400을_반환한다() throws Exception {
        given(eventService.changeStatus(3L, EventStatus.PUBLISHED))
                .willThrow(new EventException(EventErrorCode.UNSUPPORTED_STATUS_CHANGE));

        mockMvc.perform(patch("/api/admin/events/3/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "PUBLISHED" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EVENT400-2"));
    }

    @Test
    @DisplayName("게시 내리기 API 는 DRAFT 로 돌아온 이벤트 상세를 ApiResponse 로 감싼다")
    void 게시_내리기에_성공한다() throws Exception {
        EventDetailResponse unpublished = new EventDetailResponse(
                3L, "가을 멤버십 더블 혜택", EventStatus.DRAFT,
                OffsetDateTime.parse("2026-09-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-09-30T23:59:59+09:00"),
                OffsetDateTime.parse("2026-09-20T13:00:00+09:00"),
                "member_appreciation", MembershipGrade.NORMAL,
                null, false);
        given(eventService.unpublish(3L)).willReturn(unpublished);

        mockMvc.perform(post("/api/admin/events/3/unpublish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(3))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @DisplayName("게시 중이 아닌 이벤트의 게시 내리기는 409 와 EVENT409-7 을 반환한다")
    void 게시중이_아닌_이벤트의_게시_내리기는_409를_반환한다() throws Exception {
        given(eventService.unpublish(2L))
                .willThrow(new EventException(EventErrorCode.EVENT_NOT_UNPUBLISHABLE));

        mockMvc.perform(post("/api/admin/events/2/unpublish"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT409-7"));
    }

    @Test
    @DisplayName("종료 시각이 지난 이벤트의 게시 내리기는 409 와 EVENT409-1 을 반환한다")
    void 종료_시각이_지난_이벤트의_게시_내리기는_409를_반환한다() throws Exception {
        given(eventService.unpublish(4L))
                .willThrow(new EventException(EventErrorCode.EVENT_ENDED_NOT_EDITABLE));

        mockMvc.perform(post("/api/admin/events/4/unpublish"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT409-1"));
    }

    @Test
    @DisplayName("없는 이벤트의 게시 내리기는 404 와 EVENT404-0 을 반환한다")
    void 없는_이벤트의_게시_내리기는_404를_반환한다() throws Exception {
        given(eventService.unpublish(999L))
                .willThrow(new EventException(EventErrorCode.EVENT_NOT_FOUND));

        mockMvc.perform(post("/api/admin/events/999/unpublish"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT404-0"));
    }

    @Test
    @DisplayName("게시 API 는 게시된 이벤트 상세를 반환한다")
    void 이벤트_게시에_성공한다() throws Exception {
        EventDetailResponse published = new EventDetailResponse(
                1L, "테스트 이벤트", EventStatus.PUBLISHED,
                OffsetDateTime.parse("2026-10-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-10-15T23:59:59+09:00"),
                OffsetDateTime.parse("2026-09-16T01:00:00+09:00"),
                null, MembershipGrade.NORMAL,
                "<h1>게시된 버전</h1>", false);
        given(eventService.publish(1L, 10L)).willReturn(published);

        mockMvc.perform(post("/api/admin/events/1/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "versionId": 10 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    @Test
    @DisplayName("게시할 버전을 지정하지 않으면 400 과 COMMON400-0 을 반환한다")
    void 게시_버전_누락은_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/admin/events/1/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-0"));
    }

    @Test
    @DisplayName("종료된 이벤트 게시는 409 와 EVENT409-5 를 반환한다")
    void 종료된_이벤트_게시는_409를_반환한다() throws Exception {
        given(eventService.publish(1L, 10L))
                .willThrow(new EventException(EventErrorCode.EVENT_ENDED_PUBLISH_FORBIDDEN));

        mockMvc.perform(post("/api/admin/events/1/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "versionId": 10 }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT409-5"));
    }

    @Test
    @DisplayName("존재하지 않는 버전으로 게시하면 404 와 EVENT404-3 을 반환한다")
    void 없는_버전으로_게시하면_404를_반환한다() throws Exception {
        given(eventService.publish(1L, 999L))
                .willThrow(new EventException(EventErrorCode.VERSION_NOT_FOUND));

        mockMvc.perform(post("/api/admin/events/1/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "versionId": 999 }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT404-3"));
    }
}
