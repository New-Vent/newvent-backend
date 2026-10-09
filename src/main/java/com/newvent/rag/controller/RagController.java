package com.newvent.rag.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import com.newvent.rag.dto.response.QualityTrendResponse;
import com.newvent.rag.dto.response.ReindexAllStatus;
import com.newvent.rag.dto.response.SearchPreviewResponse;
import com.newvent.rag.dto.response.VersionSimilarityResponse;
import com.newvent.rag.service.EmbeddingService;
import com.newvent.rag.service.SimilarityService;
import com.newvent.rag.service.VersionCompareService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * RAG 관리자 API. /api/admin/** 이라 ADMIN 권한이 자동으로 걸림 (SecurityConfig)
 *
 * - GET /api/admin/rag/similar-versions (VersionCompareService)
 */
@Tag(name = "RAG", description = "이벤트 버전 HTML 을 블록 단위로 잘라 임베딩해 두고 비슷한 예시를 찾는다. "
        + "검색 미리보기·프롬프트 추천은 eventId 의 이벤트를 빼고 찾고, 거리(distance)가 0.5 보다 먼 결과는 버린다.")
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
    @Operation(
            summary = "버전 하나 재색인",
            description = "그 버전의 기존 청크를 지우고 다시 임베딩해 저장한 뒤 이벤트의 색인 현황을 돌려준다. 지우기와 저장은 한 트랜잭션이다. "
                    + "이벤트 전체는 전체 재색인으로 한다. 이 이벤트의 버전이 아니면 404 (EVENT404-3)")
    @PostMapping("/reindex")
    public ApiResponse<IndexStatusResponse> reindex(@Valid @RequestBody ReindexRequest request) {
        embedding.reindex(request.eventId(), request.versionId());
        return ApiResponse.success(embedding.getStatus(request.eventId()));
    }

    /** 색인 현황. 전체 버전 중 몇 개가 들어갔는지. */
    @Operation(
            summary = "색인 현황",
            description = "이벤트의 전체 버전 중 청크가 있는 버전 수와 청크 수. pendingVersions 는 한 번도 색인되지 않은 버전 수다. "
                    + "lastIndexedAt 은 다른 API 와 달리 UTC(Z) 로 나온다.")
    @GetMapping("/index-status")
    public ApiResponse<IndexStatusResponse> indexStatus(@RequestParam Long eventId) {
        return ApiResponse.success(embedding.getStatus(eventId));
    }

    /** 검색 미리보기. 쿼리를 던지면 상위 후보와 거리를 보여준다. */
    @Operation(
            summary = "검색 미리보기",
            description = "query 와 비슷한 청크 상위 topK 개와 거리를 보여 준다. distance 는 0 이면 같고 작을수록 가깝다. "
                    + "query 가 공백이면 임베딩을 부르지 않고 빈 결과를 준다.")
    @GetMapping("/search-preview")
    public ApiResponse<SearchPreviewResponse> searchPreview(
            @RequestParam Long eventId,
            @RequestParam String query,
            @RequestParam(defaultValue = "3") @Min(1) @Max(10) int topK) {
        return ApiResponse.success(similarity.searchPreview(eventId, query, topK));
    }

    /** 프롬프트 추천. 유사 이벤트를 채팅에 넣을 초안으로 바꿔준다. */
    @Operation(
            summary = "프롬프트 추천",
            description = "비슷한 청크를 채팅창에 넣을 요청문 초안으로 바꿔 준다. prompt 는 블록 작성 가이드 뒤에 예시 문구를 붙인 것이다. "
                    + "프론트는 하나를 골라 채팅창에 그대로 넣는다. query 가 공백이면 빈 목록이다.")
    @GetMapping("/recommend-prompts")
    public ApiResponse<List<PromptCandidate>> recommendPrompts(
            @RequestParam Long eventId,
            @RequestParam String query,
            @RequestParam(defaultValue = "3") @Min(1) @Max(10) int topK) {
        return ApiResponse.success(similarity.recommendPrompts(eventId, query, topK));
    }

    /** 유사 버전 탐색. 기준 버전과 비슷한 같은 이벤트 내 다른 버전을 유사도 순으로 보여준다. */
    @Operation(
            summary = "비슷한 버전 찾기",
            description = "같은 이벤트 안에서 기준 버전(versionId)과 비슷한 다른 버전을 similarity 가 큰 순서로 보여 준다. "
                    + "기준 버전의 청크 벡터를 그대로 쓰므로 임베딩을 새로 부르지 않고, 기준 버전이 색인되지 않았으면 빈 목록이다. "
                    + "이 이벤트의 버전이 아니면 404 (EVENT404-3)")
    @GetMapping("/similar-versions")
    public ApiResponse<List<VersionSimilarityResponse>> similarVersions(
            @RequestParam Long eventId,
            @RequestParam Long versionId,
            @RequestParam(defaultValue = "3") @Min(1) @Max(10) int topK) {
        return ApiResponse.success(comparing.similarVersions(eventId, versionId, topK));
    }

    /** 주간 품질 추이. 호출 건수·RAG 사용 건수·청크 수를 주별로 묶어 보여준다. */
    @Operation(
            summary = "주간 품질 추이",
            description = "최근 weeks 주 동안 LLM 호출 수·RAG 를 쓴 호출 수·새로 만든 청크 수를 주별로 묶는다. 기록이 없는 주는 빠진다.")
    @GetMapping("/quality-trend")
    public ApiResponse<List<QualityTrendResponse>> qualityTrend(
            @RequestParam(defaultValue = "4") @Min(1) @Max(12) int weeks) {
        return ApiResponse.success(embedding.getQualityTrend(weeks));
    }

    /**
     * 전체 재색인 비동기 시작. 전역 작업은 한 번에 하나 — 이미 돌고 있으면 409.
     * 자리를 잡으면 202 + 시작 시각만 돌려주고 @Async 로 발사한다 (nginx 60초 타임아웃 회피).
     */
    @Operation(
            summary = "전체 재색인 시작",
            description = "휴지통에 없는 모든 이벤트(제목이 SEED: 로 시작하는 시드 제외)를 서버에서 비동기로 다시 색인하고 202 를 바로 돌려준다. "
                    + "한 이벤트가 실패해도 다음으로 넘어가고, 결과는 진행 상태 API 로 본다. 한 번에 하나만 돌며 매일 03:00 스케줄러와 자리를 같이 쓴다. "
                    + "이미 돌고 있으면 409 인데, 오류 코드 없이 지금 진행 상태를 본문에 담아 준다.")
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
    @Operation(
            summary = "전체 재색인 진행 상태",
            description = "running(돌고 있는지)·시작·종료 시각과 직전 실행 결과(성공·실패 이벤트 수, 실패 사유). "
                    + "서버가 재시작되면 비어 있다.")
    @GetMapping("/reindex/all/status")
    public ApiResponse<ReindexAllStatus> reindexAllStatus() {
        return ApiResponse.success(embedding.getReindexAllStatus());
    }
}
