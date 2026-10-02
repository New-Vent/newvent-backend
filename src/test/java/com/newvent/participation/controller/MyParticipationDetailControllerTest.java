package com.newvent.participation.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.Map;

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
import com.newvent.common.exception.handler.GlobalExceptionHandler;
import com.newvent.participation.dto.response.MyParticipationDetailResponse;
import com.newvent.participation.exception.ParticipationErrorCode;
import com.newvent.participation.exception.ParticipationException;
import com.newvent.participation.service.MyParticipationDetailService;

@WebMvcTest(MyParticipationDetailController.class)
@Import({
        GlobalExceptionHandler.class,
        SecurityConfig.class,
        JwtProvider.class
})
class MyParticipationDetailControllerTest {

    private static final String URL = "/api/users/me/participations/{participationId}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private MyParticipationDetailService service;

    @Test
    void 상세_조회는_JWT_사용자_ID로_조회하고_공통_응답을_반환한다() throws Exception {
        when(service.getParticipation(7L, 30L))
                .thenReturn(response(
                        Map.of("pouchIndex", 2),
                        Map.of("status", "WON", "prizeName", "커피 쿠폰")
                ));

        mockMvc.perform(get(URL, 30L)
                        .header(HttpHeaders.AUTHORIZATION, userToken(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.participationId").value(30))
                .andExpect(jsonPath("$.data.eventId").value(10))
                .andExpect(jsonPath("$.data.eventTitle").value("복주머니 이벤트"))
                .andExpect(jsonPath("$.data.submittedData.pouchIndex").value(2))
                .andExpect(jsonPath("$.data.resultData.status").value("WON"))
                .andExpect(jsonPath("$.data.resultData.prizeName").value("커피 쿠폰"));

        verify(service).getParticipation(7L, 30L);
    }

    @Test
    void 빈_제출값과_결과는_JSON_객체로_반환한다() throws Exception {
        when(service.getParticipation(7L, 30L)).thenReturn(response(Map.of(), Map.of()));

        mockMvc.perform(get(URL, 30L)
                        .header(HttpHeaders.AUTHORIZATION, userToken(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submittedData").isMap())
                .andExpect(jsonPath("$.data.submittedData").isEmpty())
                .andExpect(jsonPath("$.data.resultData").isMap())
                .andExpect(jsonPath("$.data.resultData").isEmpty());
    }

    @Test
    void 기록이_없으면_404를_반환한다() throws Exception {
        when(service.getParticipation(7L, 999L))
                .thenThrow(new ParticipationException(ParticipationErrorCode.PARTICIPATION_NOT_FOUND));

        mockMvc.perform(get(URL, 999L)
                        .header(HttpHeaders.AUTHORIZATION, userToken(7L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void 다른_사용자의_기록이면_404를_반환한다() throws Exception {
        when(service.getParticipation(8L, 30L))
                .thenThrow(new ParticipationException(ParticipationErrorCode.PARTICIPATION_NOT_FOUND));

        mockMvc.perform(get(URL, 30L)
                        .header(HttpHeaders.AUTHORIZATION, userToken(8L)))
                .andExpect(status().isNotFound());

        verify(service).getParticipation(8L, 30L);
    }

    @Test
    void 참여_ID가_0이면_400을_반환한다() throws Exception {
        mockMvc.perform(get(URL, 0)
                        .header(HttpHeaders.AUTHORIZATION, userToken(7L)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void 참여_ID가_음수이면_400을_반환한다() throws Exception {
        mockMvc.perform(get(URL, -1)
                        .header(HttpHeaders.AUTHORIZATION, userToken(7L)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void 참여_ID가_숫자가_아니면_400을_반환한다() throws Exception {
        mockMvc.perform(get(URL, "invalid")
                        .header(HttpHeaders.AUTHORIZATION, userToken(7L)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void 인증이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(get(URL, 30L))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void 관리자_토큰이면_403을_반환한다() throws Exception {
        String token = "Bearer "
                + jwtProvider.issue(AuthUser.admin(5L)).value();

        mockMvc.perform(get(URL, 30L)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    private String userToken(Long userId) {
        return "Bearer " + jwtProvider.issue(AuthUser.user(userId)).value();
    }

    private MyParticipationDetailResponse response(
            Map<String, Object> submittedData,
            Map<String, Object> resultData
    ) {
        return MyParticipationDetailResponse.builder()
                .participationId(30L)
                .eventId(10L)
                .eventTitle("복주머니 이벤트")
                .participatedAt(OffsetDateTime.parse("2026-10-02T11:00:00+09:00"))
                .submittedData(submittedData)
                .resultData(resultData)
                .build();
    }
}
