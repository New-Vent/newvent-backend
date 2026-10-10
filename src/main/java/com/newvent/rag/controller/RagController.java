package com.newvent.rag.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.auth.dto.AuthUser;
import com.newvent.common.response.ApiResponse;
import com.newvent.event.service.EventOwnerCheck;
import com.newvent.rag.dto.request.ReindexRequest;
import com.newvent.rag.dto.response.IndexStatusResponse;
import com.newvent.rag.dto.response.PromptCandidate;
import com.newvent.rag.dto.response.QualityTrendResponse;
import com.newvent.rag.dto.response.ReindexAllStatus;
import com.newvent.rag.dto.response.SearchPreviewResponse;
import com.newvent.rag.dto.response.VersionSimilarityResponse;
import com.newvent.rag.service.EmbeddingService;
import com.newvent.rag.service.SimilarityService;
import com.newvent.rag.service.VersionCompareService;

/**
 * RAG 관리자 API. /api/admin/** 이라 ADMIN 권한이 자동으로 걸림 (SecurityConfig)
 *
 * - GET /api/admin/rag/similar-versions (VersionCompareService)
 *
 * ★ eventId 를 받는 API 는 이벤트 소유 관리자만 부를 수 있다 (EventOwnerCheck).
 *   품질 추이 · 전체 재색인은 이벤트 하나에 묶이지 않아 관리자면 누구나 부른다.
 */
@Validated
@RestController
@RequestMapping("/api/admin/rag")
public class RagController {

	private final EmbeddingService embedding;
	private final SimilarityService similarity;
	private final VersionCompareService comparing;
	private final EventOwnerCheck ownerCheck;

	public RagController(EmbeddingService embedding, SimilarityService similarity,
			VersionCompareService comparing, EventOwnerCheck ownerCheck) {
		this.embedding = embedding;
		this.similarity = similarity;
		this.comparing = comparing;
		this.ownerCheck = ownerCheck;
	}

    /** 수동 재색인. versionId가 없으면 이벤트 전체. 돌리고 나서 현황을 돌려준다. */
    @PostMapping("/reindex")
    public ApiResponse<IndexStatusResponse> reindex(@Valid @RequestBody ReindexRequest request,
                                                    @AuthenticationPrincipal AuthUser admin) {
        ownerCheck.requireOwned(request.eventId(), admin);
        embedding.reindex(request.eventId(), request.versionId());
        return ApiResponse.success(embedding.getStatus(request.eventId()));
    }

    /** 색인 현황. 전체 버전 중 몇 개가 들어갔는지. */
    @GetMapping("/index-status")
    public ApiResponse<IndexStatusResponse> indexStatus(@RequestParam Long eventId,
                                                        @AuthenticationPrincipal AuthUser admin) {
        ownerCheck.requireOwned(eventId, admin);
        return ApiResponse.success(embedding.getStatus(eventId));
    }

    /** 검색 미리보기. 쿼리를 던지면 상위 후보와 거리를 보여준다. */
    @GetMapping("/search-preview")
    public ApiResponse<SearchPreviewResponse> searchPreview(
            @RequestParam Long eventId,
            @RequestParam String query,
            @RequestParam(defaultValue = "3") @Min(1) @Max(10) int topK,
            @AuthenticationPrincipal AuthUser admin) {
        ownerCheck.requireOwned(eventId, admin);
        return ApiResponse.success(similarity.searchPreview(eventId, query, topK));
    }

    /** 프롬프트 추천. 유사 이벤트를 채팅에 넣을 초안으로 바꿔준다. */
    @GetMapping("/recommend-prompts")
    public ApiResponse<List<PromptCandidate>> recommendPrompts(
            @RequestParam Long eventId,
            @RequestParam String query,
            @RequestParam(defaultValue = "3") @Min(1) @Max(10) int topK,
            @AuthenticationPrincipal AuthUser admin) {
        ownerCheck.requireOwned(eventId, admin);
        return ApiResponse.success(similarity.recommendPrompts(eventId, query, topK));
    }

    /** 유사 버전 탐색. 기준 버전과 비슷한 같은 이벤트 내 다른 버전을 유사도 순으로 보여준다. */
    @GetMapping("/similar-versions")
    public ApiResponse<List<VersionSimilarityResponse>> similarVersions(
            @RequestParam Long eventId,
            @RequestParam Long versionId,
            @RequestParam(defaultValue = "3") @Min(1) @Max(10) int topK,
            @AuthenticationPrincipal AuthUser admin) {
        ownerCheck.requireOwned(eventId, admin);
        return ApiResponse.success(comparing.similarVersions(eventId, versionId, topK));
    }

    /** 주간 품질 추이. 호출 건수·RAG 사용 건수·청크 수를 주별로 묶어 보여준다. */
    @GetMapping("/quality-trend")
    public ApiResponse<List<QualityTrendResponse>> qualityTrend(
            @RequestParam(defaultValue = "4") @Min(1) @Max(12) int weeks) {
        return ApiResponse.success(embedding.getQualityTrend(weeks));
    }

    /**
     * 전체 재색인 비동기 시작. 전역 작업은 한 번에 하나 — 이미 돌고 있으면 409.
     * 자리를 잡으면 202 + 시작 시각만 돌려주고 @Async 로 발사한다 (nginx 60초 타임아웃 회피).
     */
    @PostMapping("/reindex/all")
    public ResponseEntity<ApiResponse<ReindexAllStatus>> reindexAll() {
        if (!embedding.tryClaimReindexSlot()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.success(embedding.getReindexAllStatus()));
        }
        embedding.indexAllEventsAsync();
        return ResponseEntity.accepted()
                .body(ApiResponse.success(embedding.getReindexAllStatus()));
    }

    /** 전체 재색인 진행 상태. running·시작·종료·직전 결과. */
    @GetMapping("/reindex/all/status")
    public ApiResponse<ReindexAllStatus> reindexAllStatus() {
        return ApiResponse.success(embedding.getReindexAllStatus());
    }
}
