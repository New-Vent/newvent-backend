package com.newvent.participation.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.OffsetDateTime;
import java.util.List;

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
import com.newvent.event.dto.response.PageResponse;
import com.newvent.participation.dto.request.ParticipationListFilter;
import com.newvent.participation.dto.response.MyParticipationListResponse;
import com.newvent.participation.dto.response.MyParticipationResponse;
import com.newvent.participation.dto.response.ParticipationSummaryResponse;
import com.newvent.participation.service.MyParticipationService;

@WebMvcTest(MyParticipationController.class)
@Import({
        GlobalExceptionHandler.class,
        SecurityConfig.class,
        JwtProvider.class
})
class MyParticipationControllerTest {

    private static final String URL = "/api/users/me/participations";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private MyParticipationService service;

    @Test
    void 파라미터를_생략하면_전체_목록의_첫_페이지를_조회한다()
            throws Exception {
        when(service.getParticipations(
                7L, ParticipationListFilter.ALL, 0, 10
        )).thenReturn(response());

        mockMvc.perform(get(URL)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                userToken()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath(
                        "$.data.summary.totalParticipationCount"
                ).value(12))
                .andExpect(jsonPath(
                        "$.data.summary.rewardCount"
                ).value(3))
                .andExpect(jsonPath(
                        "$.data.summary.pendingCount"
                ).value(4))
                .andExpect(jsonPath(
                        "$.data.participations.content[0].participationId"
                ).value(30))
                .andExpect(jsonPath(
                        "$.data.participations.content[0].prizeName"
                ).value("커피 쿠폰"));

        verify(service).getParticipations(
                7L, ParticipationListFilter.ALL, 0, 10
        );
    }

    @Test
    void 필터와_페이지_값을_서비스에_전달한다() throws Exception {
        when(service.getParticipations(
                7L, ParticipationListFilter.REWARDS, 1, 2
        )).thenReturn(new MyParticipationListResponse(
                new ParticipationSummaryResponse(12, 3, 4),
                PageResponse.of(List.of(), 1, 2, 3)
        ));

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, userToken())
                        .param("filter", "REWARDS")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.participations.page"
                ).value(1))
                .andExpect(jsonPath(
                        "$.data.participations.size"
                ).value(2))
                .andExpect(jsonPath(
                        "$.data.participations.totalElements"
                ).value(3));

        verify(service).getParticipations(
                7L, ParticipationListFilter.REWARDS, 1, 2
        );
    }

    @Test
    void 잘못된_필터는_400을_반환한다() throws Exception {
        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, userToken())
                        .param("filter", "UNKNOWN"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void 음수_페이지는_400을_반환한다() throws Exception {
        assertInvalidParameter("page", "-1");
    }

    @Test
    void 크기가_0이면_400을_반환한다() throws Exception {
        assertInvalidParameter("size", "0");
    }

    @Test
    void 크기가_50을_초과하면_400을_반환한다() throws Exception {
        assertInvalidParameter("size", "51");
    }

    @Test
    void 인증이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void 관리자_토큰으로_조회하면_403을_반환한다() throws Exception {
        String token = "Bearer "
                + jwtProvider.issue(AuthUser.admin(5L)).value();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    private void assertInvalidParameter(
            String name,
            String value
    ) throws Exception {
        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, userToken())
                        .param(name, value))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    private String userToken() {
        return "Bearer "
                + jwtProvider.issue(AuthUser.user(7L)).value();
    }

    private MyParticipationListResponse response() {
        MyParticipationResponse item = new MyParticipationResponse(
                30L,
                10L,
                "가을 이벤트",
                OffsetDateTime.parse("2026-10-01T15:00:00+09:00"),
                "WON",
                "커피 쿠폰"
        );

        return new MyParticipationListResponse(
                new ParticipationSummaryResponse(12, 3, 4),
                PageResponse.of(List.of(item), 0, 10, 12)
        );
    }
}
