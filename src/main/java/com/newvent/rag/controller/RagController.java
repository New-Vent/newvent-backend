package com.newvent.rag.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.common.response.ApiResponse;
import com.newvent.rag.dto.request.ReindexRequest;
import com.newvent.rag.dto.response.IndexStatusResponse;
import com.newvent.rag.dto.response.PromptCandidate;
import com.newvent.rag.dto.response.SearchPreviewResponse;
import com.newvent.rag.dto.response.VersionSimilarityResponse;
import com.newvent.rag.service.EmbeddingService;
import com.newvent.rag.service.SimilarityService;
import com.newvent.rag.service.VersionCompareService;

/**
 * RAG 관리자 API. /api/admin/** 이라 ADMIN 권한이 자동으로 걸림 (SecurityConfig)
 *
 * - GET /api/admin/rag/similar-versions (VersionCompareService)
 */
@Validated
@RestController
@RequestMapping("/api/admin/rag")
public class RagController {

	private final EmbeddingService embedding;
	private final SimilarityService similarity;
	private final VersionCompareService comparing;

	public RagController(EmbeddingService embedding, SimilarityService similarity,
			VersionCompareService comparing) {
		this.embedding = embedding;
		this.similarity = similarity;
		this.comparing = comparing;
	}

    /** 수동 재색인. versionId가 없으면 이벤트 전체. 돌리고 나서 현황을 돌려준다. */
    @PostMapping("/reindex")
    public ApiResponse<IndexStatusResponse> reindex(@Valid @RequestBody ReindexRequest request) {
        embedding.reindex(request.eventId(), request.versionId());
        return ApiResponse.success(embedding.getStatus(request.eventId()));
    }

    /** 색인 현황. 전체 버전 중 몇 개가 들어갔는지. */
    @GetMapping("/index-status")
    public ApiResponse<IndexStatusResponse> indexStatus(@RequestParam Long eventId) {
        return ApiResponse.success(embedding.getStatus(eventId));
    }

    /** 검색 미리보기. 쿼리를 던지면 상위 후보와 거리를 보여준다. */
    @GetMapping("/search-preview")
    public ApiResponse<SearchPreviewResponse> searchPreview(
            @RequestParam Long eventId,
            @RequestParam String query,
            @RequestParam(defaultValue = "3") @Min(1) @Max(10) int topK) {
        return ApiResponse.success(similarity.searchPreview(eventId, query, topK));
    }

    /** 프롬프트 추천. 유사 이벤트를 채팅에 넣을 초안으로 바꿔준다. */
    @GetMapping("/recommend-prompts")
    public ApiResponse<List<PromptCandidate>> recommendPrompts(
            @RequestParam Long eventId,
            @RequestParam String query,
            @RequestParam(defaultValue = "3") @Min(1) @Max(10) int topK) {
        return ApiResponse.success(similarity.recommendPrompts(eventId, query, topK));
    }

    /** 유사 버전 탐색. 기준 버전과 비슷한 같은 이벤트 내 다른 버전을 유사도 순으로 보여준다. */
    @GetMapping("/similar-versions")
    public ApiResponse<List<VersionSimilarityResponse>> similarVersions(
            @RequestParam Long eventId,
            @RequestParam Long versionId,
            @RequestParam(defaultValue = "3") @Min(1) @Max(10) int topK) {
        return ApiResponse.success(comparing.similarVersions(eventId, versionId, topK));
    }
}
