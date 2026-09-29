package com.newvent.event.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.newvent.user.domain.MembershipGrade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.newvent.auth.jwt.JwtProvider;
import com.newvent.common.config.SecurityConfig;
import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventProgress;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.domain.EventVersion;
import com.newvent.event.dto.response.PageResponse;
import com.newvent.event.dto.response.PublicEventSummaryResponse;
import com.newvent.event.exception.EventNotAccessibleException;
import com.newvent.event.exception.EventNotFoundException;
import com.newvent.event.service.PublicEventService;

// @WebMvcTest 는 SecurityConfig 를 스캔하지 않는다 — 안 넣으면 Spring Security 기본 설정(전부 인증 + CSRF)이 걸린다.
// 실제 인가 규칙(GET /api/public/events/* 허용)으로 검증하려고 직접 import 한다.
@WebMvcTest(PublicEventController.class)
@Import({SecurityConfig.class, JwtProvider.class})
class PublicEventApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicEventService publicEventService;

    @Test
    @DisplayName("공개 이벤트 상세 조회 성공 시 200과 게시 HTML·마감임박 여부를 반환")
    void 상세조회_성공() throws Exception {
        EventVersion publishedVersion = newEventVersion("<h1>hello</h1>");
        Event event = newEvent(1L, "가을 이벤트", "https://newvent.example/e/1", publishedVersion);

        ReflectionTestUtils.setField(event, "grade", MembershipGrade.EXCELLENT);

        when(publicEventService.getPublicEvent(1L)).thenReturn(event);
        when(publicEventService.isClosingSoon(any(), any())).thenReturn(true);

        mockMvc.perform(get("/api/public/events/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("가을 이벤트"))
                .andExpect(jsonPath("$.data.grade").value("EXCELLENT"))
                .andExpect(jsonPath("$.data.url").value("https://newvent.example/e/1"))
                .andExpect(jsonPath("$.data.publishedHtml").value("<h1>hello</h1>"))
                .andExpect(jsonPath("$.data.closingSoon").value(true));
    }

    @Test
    @DisplayName("게시 버전이 없는 이벤트는 publishedHtml을 null로 반환")
    void 상세조회_게시버전없음() throws Exception {
        Event event = newEvent(1L, "가을 이벤트", "https://newvent.example/e/1", null);
        when(publicEventService.getPublicEvent(1L)).thenReturn(event);
        when(publicEventService.isClosingSoon(any(), any())).thenReturn(false);

        mockMvc.perform(get("/api/public/events/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publishedHtml").doesNotExist());
    }

    @Test
    @DisplayName("존재하지 않는 이벤트를 조회하면 404를 반환")
    void 상세조회_없는이벤트_404() throws Exception {
        when(publicEventService.getPublicEvent(anyLong())).thenThrow(new EventNotFoundException());

        mockMvc.perform(get("/api/public/events/999")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("공개 기간 밖의 이벤트를 조회하면 404를 반환")
    void 상세조회_접근불가_404() throws Exception {
        when(publicEventService.getPublicEvent(anyLong())).thenThrow(new EventNotAccessibleException());

        mockMvc.perform(get("/api/public/events/1")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("목록 조회 성공 시 200과 PageResponse 형태를 반환")
    void 목록조회_성공() throws Exception {
        PublicEventSummaryResponse summary = new PublicEventSummaryResponse(
                1L, "가을 이벤트", null, null, EventStatus.PUBLISHED, "signup", true);
        PageResponse<PublicEventSummaryResponse> page = PageResponse.of(List.of(summary), 0, 10, 1);
        when(publicEventService.getPublicEvents(any(), any(), any(), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/api/public/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("가을 이벤트"))
                .andExpect(jsonPath("$.data.content[0].category").value("signup"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("쿼리 파라미터를 그대로 서비스에 전달")
    void 목록조회_쿼리파라미터_전달() throws Exception {
        when(publicEventService.getPublicEvents(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(PageResponse.of(List.of(), 1, 20, 0));

        mockMvc.perform(get("/api/public/events")
                        .param("category", "signup")
                        .param("keyword", "가을")
                        .param("progress", "ONGOING")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk());

        verify(publicEventService).getPublicEvents(eq("signup"), eq("가을"), eq(EventProgress.ONGOING), eq(1), eq(20));
    }

    @Test
    @DisplayName("파라미터를 생략하면 기본값(page=0, size=10)과 null로 서비스에 전달")
    void 목록조회_기본값() throws Exception {
        when(publicEventService.getPublicEvents(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(PageResponse.of(List.of(), 0, 10, 0));

        mockMvc.perform(get("/api/public/events")).andExpect(status().isOk());

        verify(publicEventService).getPublicEvents(isNull(), isNull(), isNull(), eq(0), eq(10));
    }

    @Test
    @DisplayName("size가 50을 초과하면 400을 반환")
    void 목록조회_size_초과_400() throws Exception {
        mockMvc.perform(get("/api/public/events").param("size", "51")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("page가 음수면 400을 반환")
    void 목록조회_page_음수_400() throws Exception {
        mockMvc.perform(get("/api/public/events").param("page", "-1")).andExpect(status().isBadRequest());
    }

    private Event newEvent(Long id, String title, String url, EventVersion publishedVersion) {
        Event event = BeanUtils.instantiateClass(Event.class);
        ReflectionTestUtils.setField(event, "id", id);
        ReflectionTestUtils.setField(event, "title", title);
        ReflectionTestUtils.setField(event, "status", EventStatus.PUBLISHED);
        ReflectionTestUtils.setField(event, "url", url);
        ReflectionTestUtils.setField(event, "publishedVersion", publishedVersion);
        return event;
    }

    private EventVersion newEventVersion(String htmlContent) {
        EventVersion version = BeanUtils.instantiateClass(EventVersion.class);
        ReflectionTestUtils.setField(version, "htmlContent", htmlContent);
        return version;
    }
}
