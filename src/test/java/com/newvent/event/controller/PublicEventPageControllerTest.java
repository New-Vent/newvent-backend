package com.newvent.event.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.newvent.auth.jwt.JwtProvider;
import com.newvent.common.config.SecurityConfig;
import com.newvent.event.domain.Event;
import com.newvent.event.exception.EventNotAccessibleException;
import com.newvent.event.exception.EventNotFoundException;
import com.newvent.event.service.PublicEventService;

@WebMvcTest(PublicEventPageController.class)
@Import({SecurityConfig.class, JwtProvider.class})
class PublicEventPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicEventService publicEventService;

    @Test
    void anonymousVisitorReceivesPublishedHtmlWithFilledSlotsAndStyles() throws Exception {
        Event event = BeanUtils.instantiateClass(Event.class);
        ReflectionTestUtils.setField(event, "title", "테스트 <이벤트>");
        String filled = "<div class=\"ev-container event-page\">"
                + "<section data-block=\"hero\"><p data-slot=\"period\">10월 6일 ~ 10월 8일</p></section>"
                + "<script>window.newVentReinit = function () {};</script></div>";
        when(publicEventService.getPublicEvent(1L)).thenReturn(event);
        when(publicEventService.publishedHtmlOf(event)).thenReturn(filled);

        mockMvc.perform(get("/e/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html;charset=UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("<!DOCTYPE html>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<title>테스트 &lt;이벤트&gt;</title>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/assets/event.css")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-slot=\"period\">10월 6일 ~ 10월 8일")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<script>window.newVentReinit = function () {};</script>")));
        verify(publicEventService).publishedHtmlOf(event);
    }

    @Test
    void inaccessibleAndUnknownEventsReturn404() throws Exception {
        when(publicEventService.getPublicEvent(2L)).thenThrow(new EventNotFoundException());
        when(publicEventService.getPublicEvent(3L)).thenThrow(new EventNotAccessibleException());
        when(publicEventService.getPublicEvent(99L)).thenThrow(new EventNotFoundException());

        mockMvc.perform(get("/e/2")).andExpect(status().isNotFound());
        mockMvc.perform(get("/e/3")).andExpect(status().isNotFound());
        mockMvc.perform(get("/e/99")).andExpect(status().isNotFound());
    }

    @Test
    void missingPublishedVersionReturns404() throws Exception {
        Event event = BeanUtils.instantiateClass(Event.class);
        when(publicEventService.getPublicEvent(1L)).thenReturn(event);
        mockMvc.perform(get("/e/1")).andExpect(status().isNotFound());
    }

    @Test
    void invalidIdReturns400() throws Exception {
        mockMvc.perform(get("/e/abc")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void postIsNotMapped() throws Exception {
        mockMvc.perform(post("/e/1")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void anonymousPostIsNotPublic() throws Exception {
        mockMvc.perform(post("/e/1")).andExpect(status().isUnauthorized());
    }
}
