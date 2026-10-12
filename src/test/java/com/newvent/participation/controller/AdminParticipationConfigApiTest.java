package com.newvent.participation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.newvent.auth.dto.AuthUser;
import com.newvent.auth.jwt.JwtProvider;
import com.newvent.common.config.SecurityConfig;
import com.newvent.participation.dto.response.ParticipationConfigResponse;
import com.newvent.participation.service.ParticipationConfigService;

@WebMvcTest(AdminParticipationConfigController.class)
@Import({SecurityConfig.class, JwtProvider.class})
class AdminParticipationConfigApiTest {

    private static final String PATH = "/api/admin/events/1/participation-config";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private ParticipationConfigService configService;

    @Test
    void 관리자토큰으로_저장하면_토큰의관리자를전달한다() throws Exception {
        Map<String, Object> saved = Map.of("resultMode", "GUARANTEED", "prizeName", "쿠폰");
        when(configService.save(eq(1L), any(AuthUser.class), eq("BASIC"), any()))
                .thenReturn(new ParticipationConfigResponse(1L, "BASIC", saved));

        mockMvc.perform(put(PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.admin(3L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameCode":"BASIC",
                                 "config":{"resultMode":"GUARANTEED","prizeName":"쿠폰"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventId").value(1))
                .andExpect(jsonPath("$.data.gameCode").value("BASIC"))
                .andExpect(jsonPath("$.data.config.prizeName").value("쿠폰"));

        verify(configService).save(eq(1L), any(AuthUser.class), eq("BASIC"), any());
    }

    @Test
    @DisplayName("gameCode 가 비면 400 이다 — 서비스까지 가지 않는다")
    void gameCode가없으면_400이다() throws Exception {
        mockMvc.perform(put(PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.admin(3L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"config\":{}}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(configService);
    }

    @Test
    @DisplayName("config 를 안 보내면 빈 설정으로 본다 — 사전예약형은 설정이 없다")
    void config가없으면_빈설정이다() throws Exception {
        when(configService.save(eq(1L), any(AuthUser.class), eq("PRE_REGISTRATION"), eq(Map.of())))
                .thenReturn(new ParticipationConfigResponse(1L, "PRE_REGISTRATION", Map.of()));

        mockMvc.perform(put(PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.admin(3L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameCode\":\"PRE_REGISTRATION\"}"))
                .andExpect(status().isOk());

        verify(configService).save(eq(1L), any(AuthUser.class), eq("PRE_REGISTRATION"), eq(Map.of()));
    }

    @Test
    void 설정을_조회한다() throws Exception {
        when(configService.find(eq(1L), any(AuthUser.class)))
                .thenReturn(new ParticipationConfigResponse(1L, null, Map.of()));

        mockMvc.perform(get(PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.admin(3L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gameCode").value((Object) null))
                .andExpect(jsonPath("$.data.config").isMap());
    }

    @Test
    @DisplayName("사용자 토큰으로는 부를 수 없다 — /api/admin/** 은 ADMIN 전용")
    void 사용자토큰은_403이다() throws Exception {
        mockMvc.perform(get(PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(AuthUser.user(7L))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(configService);
    }

    @Test
    void 토큰이없으면_401이다() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        verifyNoInteractions(configService);
    }

    private String bearer(AuthUser user) {
        return "Bearer " + jwtProvider.issue(user).value();
    }
}
