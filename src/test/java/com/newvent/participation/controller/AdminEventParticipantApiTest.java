package com.newvent.participation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
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
import com.newvent.common.response.PageResponse;
import com.newvent.participation.domain.ParticipationResultStatus;
import com.newvent.participation.dto.response.EventParticipantListResponse;
import com.newvent.participation.dto.response.EventParticipantResponse;
import com.newvent.participation.dto.response.ParticipationSummaryResponse;
import com.newvent.participation.service.EventParticipantService;

@WebMvcTest(AdminEventParticipantController.class)
@Import({SecurityConfig.class, JwtProvider.class})
class AdminEventParticipantApiTest {

    private static final String PATH = "/api/admin/events/1/participations";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private EventParticipantService participantService;

    @Test
    void 참여자목록을_조회한다() throws Exception {
        when(participantService.getParticipants(eq(1L), any(AuthUser.class), isNull(), eq(0), eq(20)))
                .thenReturn(listOf(EventParticipantResponse.builder()
                        .participationId(6699L)
                        .userId(3L)
                        .userName("권도하")
                        .participatedAt(OffsetDateTime.now())
                        .submittedData(Map.of())
                        .resultStatus("WON")
                        .prizeName("데이터 5GB 쿠폰")
                        .build()));

        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.admin(3L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.totalParticipationCount").value(20))
                .andExpect(jsonPath("$.data.participants.content[0].userName").value("권도하"))
                .andExpect(jsonPath("$.data.participants.content[0].resultStatus").value("WON"));
    }

    @Test
    @DisplayName("resultStatus=WON 이 enum 으로 넘어간다 — 당첨자 명단")
    void 당첨자만_거른다() throws Exception {
        when(participantService.getParticipants(
                eq(1L), any(AuthUser.class), eq(ParticipationResultStatus.WON), eq(0), eq(20)))
                .thenReturn(listOf());

        mockMvc.perform(get(PATH)
                        .param("resultStatus", "WON")
                        .header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.admin(3L))))
                .andExpect(status().isOk());

        verify(participantService).getParticipants(
                eq(1L), any(AuthUser.class), eq(ParticipationResultStatus.WON), eq(0), eq(20));
    }

    @Test
    @DisplayName("없는 resultStatus 는 400 이다 — 서비스까지 가지 않는다")
    void 모르는상태는_400이다() throws Exception {
        mockMvc.perform(get(PATH)
                        .param("resultStatus", "JACKPOT")
                        .header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.admin(3L))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(participantService);
    }

    @Test
    @DisplayName("size 상한을 넘기면 400 이다 — 한 번에 전화번호 전체를 긁어가지 못하게")
    void size상한을_넘기면_400이다() throws Exception {
        mockMvc.perform(get(PATH)
                        .param("size", "100000")
                        .header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.admin(3L))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(participantService);
    }

    @Test
    @DisplayName("사용자 토큰으로는 부를 수 없다 — /api/admin/** 은 ADMIN 전용")
    void 사용자토큰은_403이다() throws Exception {
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.user(7L))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(participantService);
    }

    @Test
    void 토큰이없으면_401이다() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        verifyNoInteractions(participantService);
    }

    private EventParticipantListResponse listOf(EventParticipantResponse... rows) {
        return new EventParticipantListResponse(
                ParticipationSummaryResponse.builder()
                        .totalParticipationCount(20).rewardCount(7).pendingCount(0).build(),
                PageResponse.of(List.of(rows), 0, 20, rows.length));
    }

    private String bearer(AuthUser user) {
        return "Bearer " + jwtProvider.issue(user).value();
    }
}
