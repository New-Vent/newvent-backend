package com.newvent.event.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.newvent.common.error.GlobalExceptionHandler;
import com.newvent.common.response.PageResponse;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.domain.MembershipGrade;
import com.newvent.event.dto.EventCreateRequest;
import com.newvent.event.dto.EventDetailResponse;
import com.newvent.event.dto.EventSummaryResponse;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.service.EventService;

@WebMvcTest(AdminEventController.class)
@Import(GlobalExceptionHandler.class)
class AdminEventControllerTest {

    @Autowired
    MockMvc mockMvc;

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
                "signup", null, List.of(MembershipGrade.NORMAL), false);
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
                "instant", null, List.of(MembershipGrade.NORMAL),
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
                "sports_cheer", null, List.of(MembershipGrade.NORMAL),
                null, false);
        given(eventService.create(any(EventCreateRequest.class))).willReturn(created);

        mockMvc.perform(post("/api/admin/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "테스트 이벤트",
                                  "startAt": "2026-10-01T00:00:00+09:00",
                                  "endAt": "2026-10-15T23:59:59+09:00",
                                  "templateKey": "sports_cheer",
                                  "targetGrades": ["NORMAL"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.completedHtml").doesNotExist());
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
}
