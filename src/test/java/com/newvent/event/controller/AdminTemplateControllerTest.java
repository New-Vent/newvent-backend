package com.newvent.event.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.newvent.common.exception.handler.GlobalExceptionHandler;
import com.newvent.event.dto.TemplateResponse;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.service.EventTemplateService;

@WebMvcTest(AdminTemplateController.class)
@Import(GlobalExceptionHandler.class)
class AdminTemplateControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    EventTemplateService eventTemplateService;

    @Test
    @DisplayName("템플릿 목록 API 는 ApiResponse 로 감싼다")
    void 템플릿_목록_조회에_성공한다() throws Exception {
        given(eventTemplateService.findActiveTemplates()).willReturn(List.of(
                new TemplateResponse("sports_cheer", "스포츠 응원", "설명", "theme-sports", true)));

        mockMvc.perform(get("/api/admin/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].templateKey").value("sports_cheer"))
                .andExpect(jsonPath("$.data[0].name").value("스포츠 응원"));
    }

    @Test
    @DisplayName("없는 템플릿은 404 와 EVENT404-1 을 반환한다")
    void 존재하지_않는_템플릿_조회시_404를_반환한다() throws Exception {
        given(eventTemplateService.findByKey("없는키"))
                .willThrow(new EventException(EventErrorCode.TEMPLATE_NOT_FOUND));

        mockMvc.perform(get("/api/admin/templates/없는키"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT404-1"));
    }
}
