package com.newvent.rag.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.newvent.auth.jwt.JwtProvider;
import com.newvent.common.config.SecurityConfig;
import com.newvent.common.exception.handler.GlobalExceptionHandler;
import com.newvent.rag.dto.response.ChunkResponse;
import com.newvent.rag.dto.response.IndexStatusResponse;
import com.newvent.rag.dto.response.PromptCandidate;
import com.newvent.rag.dto.response.QualityTrendResponse;
import com.newvent.rag.dto.response.ReindexAllResponse;
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
    @DisplayName("POST /api/admin/rag/reindex — eventId 없으면 400")
    void 재색인_검증() throws Exception {
        mockMvc.perform(post("/api/admin/rag/reindex")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionId\":5}"))
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
    @DisplayName("POST /api/admin/rag/reindex/all — 전체 재색인 합계를 돌려준다")
    void 전체_재색인() throws Exception {
        given(embedding.indexAllEvents()).willReturn(
                new ReindexAllResponse(2, 2, List.of(), 15));

        mockMvc.perform(post("/api/admin/rag/reindex/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succeeded").value(2));
        verify(embedding).indexAllEvents();
    }
}
