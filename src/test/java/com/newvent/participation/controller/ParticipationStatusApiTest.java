package com.newvent.participation.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.newvent.event.exception.EventNotAccessibleException;
import com.newvent.participation.domain.ParticipationUnavailableReason;
import com.newvent.participation.dto.response.ParticipationStatusResponse;
import com.newvent.participation.service.ParticipationStatusService;

@WebMvcTest(ParticipationStatusController.class)
@Import({SecurityConfig.class, JwtProvider.class})
class ParticipationStatusApiTest {

    private static final String PATH =
            "/api/users/me/events/1/participation-status";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private ParticipationStatusService participationStatusService;

    @Test
    void 사용자토큰으로_조회하면_토큰의사용자ID를전달한다() throws Exception {
        when(participationStatusService.getStatus(1L, 7L))
                .thenReturn(new ParticipationStatusResponse(false, true, null));

        mockMvc.perform(get(PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.user(7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participated").value(false))
                .andExpect(jsonPath("$.data.canParticipate").value(true))
                .andExpect(jsonPath("$.data.unavailableReason").value((Object) null));

        verify(participationStatusService).getStatus(1L, 7L);
    }

    @Test
    void 이미참여했다면_사유코드를반환한다() throws Exception {
        when(participationStatusService.getStatus(1L, 7L))
                .thenReturn(new ParticipationStatusResponse(
                        true, false,
                        ParticipationUnavailableReason.ALREADY_PARTICIPATED
                ));

        mockMvc.perform(get(PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.user(7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participated").value(true))
                .andExpect(jsonPath("$.data.canParticipate").value(false))
                .andExpect(jsonPath("$.data.unavailableReason")
                        .value("ALREADY_PARTICIPATED"));
    }

    @Test
    void 등급이부족하면_사유코드를반환한다() throws Exception {
        when(participationStatusService.getStatus(1L, 7L))
                .thenReturn(new ParticipationStatusResponse(
                        false, false,
                        ParticipationUnavailableReason.INSUFFICIENT_GRADE
                ));

        mockMvc.perform(get(PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.user(7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participated").value(false))
                .andExpect(jsonPath("$.data.canParticipate").value(false))
                .andExpect(jsonPath("$.data.unavailableReason")
                        .value("INSUFFICIENT_GRADE"));
    }

    @Test
    void 토큰이없으면_401을반환한다() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(participationStatusService);
    }

    @Test
    void 관리자토큰이면_403을반환한다() throws Exception {
        mockMvc.perform(get(PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.admin(7L))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(participationStatusService);
    }

    @Test
    void 접근할수없는_이벤트이면_404를반환한다() throws Exception {
        when(participationStatusService.getStatus(1L, 7L))
                .thenThrow(new EventNotAccessibleException());

        mockMvc.perform(get(PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.user(7L))))
                .andExpect(status().isNotFound());
    }

    private String bearer(AuthUser principal) {
        return "Bearer " + jwtProvider.issue(principal).value();
    }
}
