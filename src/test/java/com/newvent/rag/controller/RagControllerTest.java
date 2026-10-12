package com.newvent.rag.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import com.newvent.auth.dto.AuthUser;
import com.newvent.auth.jwt.JwtProvider;
import com.newvent.common.config.SecurityConfig;
import com.newvent.common.exception.handler.GlobalExceptionHandler;
import com.newvent.event.service.EventOwnerCheck;
import com.newvent.rag.dto.response.ChunkResponse;
import com.newvent.rag.dto.response.IndexStatusResponse;
import com.newvent.rag.dto.response.PromptCandidate;
import com.newvent.rag.dto.response.QualityTrendResponse;
import com.newvent.rag.dto.response.ReindexAllStatus;
import com.newvent.rag.dto.response.SearchPreviewResponse;
import com.newvent.rag.service.EmbeddingService;
import com.newvent.rag.service.SimilarityService;
import com.newvent.rag.service.VersionCompareService;

@WebMvcTest(RagController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtProvider.class})
@WithMockUser(roles = "ADMIN")
class RagControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    EmbeddingService embedding;

    @MockitoBean
    SimilarityService similarity;

    @MockitoBean
    VersionCompareService comparing;

    @MockitoBean
    EventOwnerCheck ownerCheck;

    @Autowired
    JwtProvider jwtProvider;

    private static IndexStatusResponse indexStatus() {
        return new IndexStatusResponse(3L, 2, 1, 1, 5, Instant.parse("2026-09-30T05:00:00Z"));
    }

    @Test
    @DisplayName("POST /api/admin/rag/reindex — 돌리고 현황을 돌려준다")
    void 재색인() throws Exception {
        given(embedding.reindex(3L, 5L)).willReturn(4);
        given(embedding.getStatus(3L)).willReturn(indexStatus());

        mockMvc.perform(post("/api/admin/rag/reindex")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":3,\"versionId\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.eventId").value(3));
        verify(embedding).reindex(3L, 5L);
    }

    @Test
    @DisplayName("POST /api/admin/rag/reindex — versionId 없으면 400")
    void 재색인_versionId_필수() throws Exception {
        mockMvc.perform(post("/api/admin/rag/reindex")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":3}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/admin/rag/index-status — 색인 현황 수치를 돌려준다")
    void 현황() throws Exception {
        given(embedding.getStatus(3L)).willReturn(indexStatus());

        mockMvc.perform(get("/api/admin/rag/index-status").param("eventId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalVersions").value(2))
                .andExpect(jsonPath("$.data.pendingVersions").value(1));
    }

    @Test
    @DisplayName("GET /api/admin/rag/search-preview — 상위 청크와 거리를 돌려준다")
    void 미리보기() throws Exception {
        given(similarity.searchPreview(3L, "쿠폰", 3)).willReturn(
                new SearchPreviewResponse(3L, "쿠폰", 3,
                        List.of(new ChunkResponse(101L, "benefits", 0, "쿠폰 내용", 0.2))));

        mockMvc.perform(get("/api/admin/rag/search-preview")
                        .param("eventId", "3").param("query", "쿠폰"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results[0].blockKey").value("benefits"));
        verify(similarity).searchPreview(3L, "쿠폰", 3);
    }

    @Test
    @DisplayName("GET /api/admin/rag/recommend-prompts — 프롬프트 초안 목록을 돌려준다")
    void 프롬프트_추천() throws Exception {
        given(similarity.recommendPrompts(3L, "쿠폰", 3)).willReturn(
                List.of(new PromptCandidate(7L, "남의 이벤트", "benefits", "초안")));

        mockMvc.perform(get("/api/admin/rag/recommend-prompts")
                        .param("eventId", "3").param("query", "쿠폰"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].eventTitle").value("남의 이벤트"));
        verify(similarity).recommendPrompts(3L, "쿠폰", 3);
    }

    @Test
    @DisplayName("GET /api/admin/rag/quality-trend — 주간 건수 추이를 돌려준다")
    void 품질_추이() throws Exception {
        given(embedding.getQualityTrend(4)).willReturn(
                List.of(new QualityTrendResponse(
                        java.time.LocalDate.of(2026, 9, 21), 10L, 4L, 25L)));

        mockMvc.perform(get("/api/admin/rag/quality-trend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].ragUsedCalls").value(4));
        verify(embedding).getQualityTrend(4);
    }

    @Test
    @DisplayName("POST /api/admin/rag/reindex/all — 자리 잡으면 202 + 상태")
    void 전체_재색인_시작() throws Exception {
        given(embedding.tryClaimReindexSlot()).willReturn(true);
        given(embedding.getReindexAllStatus()).willReturn(
                new ReindexAllStatus(true, Instant.parse("2026-10-07T03:00:00Z"), null, null));

        mockMvc.perform(post("/api/admin/rag/reindex/all"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.running").value(true));
        verify(embedding).indexAllEventsAsync();
    }

    @Test
    @DisplayName("POST /api/admin/rag/reindex/all — 이미 돌고 있으면 409")
    void 전체_재색인_중복() throws Exception {
        given(embedding.tryClaimReindexSlot()).willReturn(false);
        given(embedding.getReindexAllStatus()).willReturn(
                new ReindexAllStatus(true, Instant.parse("2026-10-07T03:00:00Z"), null, null));

        mockMvc.perform(post("/api/admin/rag/reindex/all"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /api/admin/rag/reindex/all/status — 진행 상태를 돌려준다")
    void 전체_재색인_상태() throws Exception {
        given(embedding.getReindexAllStatus()).willReturn(ReindexAllStatus.idle());

        mockMvc.perform(get("/api/admin/rag/reindex/all/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.running").value(false));
    }

    @Test
    @DisplayName("eventId 를 받는 API 는 로그인한 관리자로 소유자를 확인한다")
    void 소유자_확인() throws Exception {
        given(embedding.getStatus(3L)).willReturn(indexStatus());

        mockMvc.perform(get("/api/admin/rag/index-status").param("eventId", "3")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.issue(AuthUser.admin(1L)).value()))
                .andExpect(status().isOk());
        verify(ownerCheck).requireOwned(3L, AuthUser.admin(1L));
    }

    @Test
    @DisplayName("다른 관리자의 이벤트면 eventId 를 받는 API 5개 모두 403 이고 서비스를 부르지 않는다")
    void 소유자_아니면_403() throws Exception {
        given(ownerCheck.requireOwned(anyLong(), any())).willThrow(new AccessDeniedException("denied"));

        List<RequestBuilder> requests = List.of(
                post("/api/admin/rag/reindex").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":3,\"versionId\":5}"),
                get("/api/admin/rag/index-status").param("eventId", "3"),
                get("/api/admin/rag/search-preview").param("eventId", "3").param("query", "쿠폰"),
                get("/api/admin/rag/recommend-prompts").param("eventId", "3").param("query", "쿠폰"),
                get("/api/admin/rag/similar-versions").param("eventId", "3").param("versionId", "5"));

        for (RequestBuilder request : requests) {
            mockMvc.perform(request)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("COMMON403-0"));
        }
        verifyNoInteractions(embedding, similarity, comparing);
    }

    @Test
    @DisplayName("품질 추이 · 전체 재색인은 이벤트에 묶이지 않아 소유자를 확인하지 않는다")
    void 전역_API는_소유자_확인_없음() throws Exception {
        given(embedding.getQualityTrend(anyInt())).willReturn(List.of());
        given(embedding.tryClaimReindexSlot()).willReturn(false);
        given(embedding.getReindexAllStatus()).willReturn(ReindexAllStatus.idle());

        mockMvc.perform(get("/api/admin/rag/quality-trend")).andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/rag/reindex/all")).andExpect(status().isConflict());
        mockMvc.perform(get("/api/admin/rag/reindex/all/status")).andExpect(status().isOk());
        verifyNoInteractions(ownerCheck);
    }
}
