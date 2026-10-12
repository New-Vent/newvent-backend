package com.newvent.event.controller;

import java.time.OffsetDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.auth.dto.AuthUser;
import com.newvent.common.response.ApiResponse;
import com.newvent.common.response.PageResponse;
import com.newvent.event.domain.EventProgress;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.dto.request.EventCreateRequest;
import com.newvent.event.dto.request.EventPublishRequest;
import com.newvent.event.dto.request.EventStatusChangeRequest;
import com.newvent.event.dto.request.EventUpdateRequest;
import com.newvent.event.dto.response.EventCountsResponse;
import com.newvent.event.dto.response.EventDetailResponse;
import com.newvent.event.dto.response.EventSummaryResponse;
import com.newvent.event.service.EventService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "관리자 이벤트", description = "이벤트 생성·수정·게시·종료·휴지통. 조회는 모든 관리자의 이벤트를 보여 주고 "
        + "내 이벤트인지는 ownedByMe 로 구분한다. 바꾸는 요청은 이벤트를 만든 관리자만 할 수 있다 (아니면 403 COMMON403-0)")
@Validated
@RestController
@RequestMapping("/api/admin/events")
public class AdminEventController {

    private final EventService eventService;

    public AdminEventController(EventService eventService) {
        this.eventService = eventService;
    }

    @Operation(
            summary = "이벤트 목록",
            description = "휴지통에 없는 이벤트를 최근 수정순으로 보여 준다. name 은 이벤트명 부분 일치(대소문자 무시)이고, "
                    + "숫자만 보내면 ID 가 같은 이벤트도 함께 찾는다. progress 는 저장된 상태가 아니라 지금 시각과 기간으로 판정한다. "
                    + "periodFrom·periodTo 는 이벤트 기간과 겹치는 구간이고, periodFrom 이 더 늦으면 400 (EVENT400-1)")
    @GetMapping
    public ApiResponse<PageResponse<EventSummaryResponse>> list(
            @AuthenticationPrincipal AuthUser principal,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) EventStatus status,
            @RequestParam(required = false) EventProgress progress,
            @RequestParam(required = false) OffsetDateTime periodFrom,
            @RequestParam(required = false) OffsetDateTime periodTo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.success(eventService.findAdminEvents(
                principal.id(), name, status, progress, periodFrom, periodTo, page, size));
    }

    @Operation(summary = "이벤트 상세", description = "게시 HTML(completedHtml)이 함께 내려간다. 없거나 휴지통에 있으면 404 (EVENT404-0)")
    @GetMapping("/{id}")
    public ApiResponse<EventDetailResponse> detail(@AuthenticationPrincipal AuthUser principal, @PathVariable Long id) {
        return ApiResponse.success(eventService.findAdminEvent(principal.id(), id));
    }

    // 목록 화면 상단 카운트(전체/미게시/진행중/종료). 삭제된 건 제외.
    @Operation(summary = "상태별 이벤트 개수", description = "목록 화면 상단의 전체·게시 중·미게시·종료 개수. 모든 관리자의 이벤트를 세고 휴지통은 뺀다.")
    @GetMapping("/counts")
    public ApiResponse<EventCountsResponse> counts() {
        return ApiResponse.success(eventService.findEventCounts());
    }

    @Operation(
            summary = "휴지통 목록",
            description = "복구·영구 삭제를 소유자만 할 수 있으므로 내 이벤트만 보여 준다. 최근 삭제순이고 항목 모양은 목록과 같다.")
    @GetMapping("/trash")
    public ApiResponse<PageResponse<EventSummaryResponse>> trash(
            @AuthenticationPrincipal AuthUser principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.success(eventService.findDeletedEvents(principal.id(), page, size));
    }

    @Operation(
            summary = "이벤트 생성",
            description = "DRAFT 로 만들고 요청한 관리자가 소유자가 된다. grade 를 비우면 NORMAL 이다. "
                    + "templateKey 는 기본 제공 템플릿이나 내가 등록한 활성 템플릿만 쓸 수 있다 (아니면 404 EVENT404-1). "
                    + "종료일시가 시작일시보다 늦지 않으면 400 (EVENT400-0)")
    @PostMapping
    public ResponseEntity<ApiResponse<EventDetailResponse>> create(
            @AuthenticationPrincipal AuthUser principal,
            @Valid @RequestBody EventCreateRequest request) {
        EventDetailResponse created = eventService.create(principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    // null 필드는 변경하지 않는다.
    @Operation(
            summary = "이벤트 정보·기간 수정",
            description = "보낸 필드만 바꾸고 생략하거나 null 인 필드는 그대로 둔다. templateKey 를 빈 문자열로 보내면 템플릿을 해제한다. "
                    + "상태는 바꾸지 않는다. 종료됐거나 게시 중인데 종료일시가 지났으면 409 (EVENT409-1), "
                    + "게시 중에 템플릿을 바꾸거나 해제하면 409 (EVENT409-4), 바뀐 기간의 종료일시가 시작일시보다 늦지 않으면 400 (EVENT400-0)")
    @PatchMapping("/{id}")
    public ApiResponse<EventDetailResponse> update(
            @AuthenticationPrincipal AuthUser principal,
            @PathVariable Long id,
            @Valid @RequestBody EventUpdateRequest request) {
        return ApiResponse.success(eventService.update(principal.id(), id, request));
    }

    // 소프트 삭제(deletedAt). 게시 중인 이벤트는 거부한다.
    @Operation(
            summary = "휴지통으로 보내기",
            description = "게시 중이면 409 (EVENT409-2) — 먼저 게시를 내리거나 종료한다. 페이지 생성 작업이 돌고 있으면 409 (EVENT409-3). "
                    + "참여 기록과 무관하게 보낼 수 있고 복구할 수 있다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal AuthUser principal, @PathVariable Long id) {
        eventService.delete(principal.id(), id);
        return ResponseEntity.ok(ApiResponse.successNoData());
    }

    // 휴지통에서 복구(deletedAt 해제)
    @Operation(summary = "휴지통에서 복구", description = "삭제 전 상태 그대로 돌아온다. 휴지통에 없으면 404 (EVENT404-0)")
    @PostMapping("/{id}/restore")
    public ApiResponse<EventDetailResponse> restore(@AuthenticationPrincipal AuthUser principal, @PathVariable Long id) {
        return ApiResponse.success(eventService.restore(principal.id(), id));
    }

    // 휴지통에서 영구 삭제. 복구 불가
    @Operation(
            summary = "영구 삭제",
            description = "휴지통에 있는 이벤트를 버전까지 지운다. 되돌릴 수 없다. "
                    + "휴지통에 없으면 404 (EVENT404-0), 참여 기록이 있으면 참여·당첨 이력이 함께 사라지므로 409 (EVENT409-9)")
    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<ApiResponse<Void>> hardDelete(@AuthenticationPrincipal AuthUser principal, @PathVariable Long id) {
        eventService.hardDelete(principal.id(), id);
        return ResponseEntity.ok(ApiResponse.successNoData());
    }

    // 종료(PUBLISHED → ENDED)만 받는다. 게시는 POST /{id}/publish
    @Operation(
            summary = "이벤트 종료",
            description = "status 는 ENDED 만 받는다 (아니면 400 EVENT400-2). 게시는 게시 API 로 한다. "
                    + "게시 중인 이벤트만 종료할 수 있고 (아니면 409 EVENT409-6) 되돌릴 수 없다. 게시 HTML 은 그대로 남는다.")
    @PatchMapping("/{id}/status")
    public ApiResponse<EventDetailResponse> changeStatus(
            @AuthenticationPrincipal AuthUser principal,
            @PathVariable Long id,
            @Valid @RequestBody EventStatusChangeRequest request) {
        return ApiResponse.success(eventService.changeStatus(principal.id(), id, request.status()));
    }

    // 게시(DRAFT→PUBLISHED) 또는 재게시(다른 버전으로 교체)
    @Operation(
            summary = "게시·재게시",
            description = "고른 버전을 사용자 화면에 내보낸다. 이미 게시 중이면 그 버전으로 바꾸고, 고른 버전은 저장 지점이 된다. "
                    + "시작일은 보지 않아 시작 전 이벤트는 예약 게시가 된다. 종료된 이벤트는 409 (EVENT409-5), "
                    + "종료일시가 지났으면 409 (EVENT409-8) — 기간을 고친 뒤 다시 게시한다. 이 이벤트의 버전이 아니면 404 (EVENT404-3)")
    @PostMapping("/{id}/publish")
    public ApiResponse<EventDetailResponse> publish(
            @AuthenticationPrincipal AuthUser principal,
            @PathVariable Long id,
            @Valid @RequestBody EventPublishRequest request) {
        return ApiResponse.success(eventService.publish(principal.id(), id, request.versionId()));
    }

    // 게시 내리기(PUBLISHED → DRAFT) 종료는 PATCH /{id}/status
    @Operation(
            summary = "게시 내리기",
            description = "DRAFT 로 돌려 사용자 화면에서 내린다. 버전·참여 기록은 남고 다시 게시할 수 있다. "
                    + "게시 중이 아니면 409 (EVENT409-7), 종료일시가 지났으면 자동 종료 전이라도 409 (EVENT409-1)")
    @PostMapping("/{id}/unpublish")
    public ApiResponse<EventDetailResponse> unpublish(@AuthenticationPrincipal AuthUser principal, @PathVariable Long id) {
        return ApiResponse.success(eventService.unpublish(principal.id(), id));
    }
}
