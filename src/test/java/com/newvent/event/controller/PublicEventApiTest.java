package com.newvent.event.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.newvent.event.domain.EventStatus;
import com.newvent.event.domain.EventVersion;
import com.newvent.event.exception.EventNotAccessibleException;
import com.newvent.event.exception.EventNotFoundException;
import com.newvent.event.service.PublicEventService;

// @WebMvcTest 는 SecurityConfig 를 스캔하지 않는다 — 안 넣으면 Spring Security 기본 설정(전부 인증 + CSRF)이 걸린다.
// 실제 인가 규칙(/api/public/** 허용)으로 검증하려고 직접 import 한다.
@WebMvcTest(PublicEventController.class)
@Import({SecurityConfig.class, JwtProvider.class})
class PublicEventApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicEventService publicEventService;

    @Test
    @DisplayName("공개 이벤트 상세 조회 성공 시 200과 게시 HTML·마감임박 여부를 반환한다")
    void 상세조회_성공() throws Exception {
        EventVersion publishedVersion = newEventVersion("<h1>hello</h1>");
        Event event = newEvent(1L, "가을 이벤트", "https://newvent.example/e/1", publishedVersion);
        when(publicEventService.getPublicEvent(1L)).thenReturn(event);
        when(publicEventService.isClosingSoon(any(), any())).thenReturn(true);

        mockMvc.perform(get("/api/public/events/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("가을 이벤트"))
                .andExpect(jsonPath("$.data.url").value("https://newvent.example/e/1"))
                .andExpect(jsonPath("$.data.publishedHtml").value("<h1>hello</h1>"))
                .andExpect(jsonPath("$.data.closingSoon").value(true));
    }

    @Test
    @DisplayName("게시 버전이 없는 이벤트는 publishedHtml을 null로 반환한다")
    void 상세조회_게시버전없음() throws Exception {
        Event event = newEvent(1L, "가을 이벤트", "https://newvent.example/e/1", null);
        when(publicEventService.getPublicEvent(1L)).thenReturn(event);
        when(publicEventService.isClosingSoon(any(), any())).thenReturn(false);

        mockMvc.perform(get("/api/public/events/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publishedHtml").doesNotExist());
    }

    @Test
    @DisplayName("존재하지 않는 이벤트를 조회하면 404를 반환한다")
    void 상세조회_없는이벤트_404() throws Exception {
        when(publicEventService.getPublicEvent(anyLong())).thenThrow(new EventNotFoundException());

        mockMvc.perform(get("/api/public/events/999")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("공개 기간 밖의 이벤트를 조회하면 404를 반환한다")
    void 상세조회_접근불가_404() throws Exception {
        when(publicEventService.getPublicEvent(anyLong())).thenThrow(new EventNotAccessibleException());

        mockMvc.perform(get("/api/public/events/1")).andExpect(status().isNotFound());
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
