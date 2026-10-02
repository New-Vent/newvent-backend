package com.newvent.participation.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.newvent.event.exception.EventNotAccessibleException;
import com.newvent.participation.dto.response.ParticipationCreateResponse;
import com.newvent.participation.exception.ParticipationErrorCode;
import com.newvent.participation.exception.ParticipationException;
import com.newvent.participation.service.ParticipationService;

@WebMvcTest(ParticipationController.class)
@Import({SecurityConfig.class, JwtProvider.class})
class ParticipationApiTest {

    private static final String PATH = "/api/users/me/events/1/participations";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private ParticipationService participationService;

    @Test
    void 사용자토큰으로_참여하면_201과_참여ID를반환한다() throws Exception {
        when(participationService.participate(1L, 7L, null))
                .thenReturn(new ParticipationCreateResponse(30L, 1L, Map.of()));

        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.user(7L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.participationId").value(30))
                .andExpect(jsonPath("$.data.eventId").value(1));

        verify(participationService).participate(1L, 7L, null);
    }

    @Test
    void 토큰이없으면_401을반환한다() throws Exception {
        mockMvc.perform(post(PATH))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(participationService);
    }

    @Test
    void 관리자토큰이면_403을반환한다() throws Exception {
        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.admin(7L))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(participationService);
    }

    @Test
    void 이미참여했다면_409를반환한다() throws Exception {
        when(participationService.participate(1L, 7L, null))
                .thenThrow(new ParticipationException(
                        ParticipationErrorCode.ALREADY_PARTICIPATED));

        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.user(7L))))
                .andExpect(status().isConflict());
    }

    @Test
    void 접근할수없는이벤트이면_404를반환한다() throws Exception {
        when(participationService.participate(1L, 7L, null))
                .thenThrow(new EventNotAccessibleException());

        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.user(7L))))
                .andExpect(status().isNotFound());
    }

    private String bearer(AuthUser principal) {
        return "Bearer " + jwtProvider.issue(principal).value();
    }
}
