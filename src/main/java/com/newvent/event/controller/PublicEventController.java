package com.newvent.event.controller;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.common.response.ApiResponse;
import com.newvent.common.response.PageResponse;
import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventProgress;
import com.newvent.event.dto.response.PublicEventResponse;
import com.newvent.event.dto.response.PublicEventSummaryResponse;
import com.newvent.event.service.PublicEventService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "공개 이벤트", description = "사용자 화면용. 로그인 없이 볼 수 있다.")
@Validated
@RestController
@RequestMapping("/api/public/events")
public class PublicEventController {

    private final PublicEventService publicEventService;

    public PublicEventController(PublicEventService publicEventService) {
        this.publicEventService = publicEventService;
    }

    @Operation(
            summary = "공개 이벤트 목록",
            description = "게시 중인 이벤트만 시작일 최신순으로 보여 준다. category 는 템플릿 코드, keyword 는 제목 부분 일치(대소문자 무시)다. "
                    + "progress 는 지금 시각과 기간으로 판정한다. thumbnailHtml 은 격리된 iframe 으로 그린다.")
    @GetMapping
    public ApiResponse<PageResponse<PublicEventSummaryResponse>> getEvents(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EventProgress progress,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.success(publicEventService.getPublicEvents(category, keyword, progress, page, size));
    }

    @Operation(
            summary = "공개 이벤트 상세",
            description = "게시 HTML 에 이벤트 기간을 채워 준다. 없거나 게시 전이거나 휴지통에 있으면 404 (EVENT404-0), "
                    + "공개 기간 밖이거나 종료됐으면 404 (EVENT404-2)")
    @GetMapping("/{eventId}")
    public ApiResponse<PublicEventResponse> getEvent(@PathVariable Long eventId) {
        Event event = publicEventService.getPublicEvent(eventId);
        boolean closingSoon = publicEventService.isClosingSoon(event, OffsetDateTime.now());
        // 슬롯을 채운 HTML 을 서비스에서 받아 넘긴다. 엔티티에서 직접 꺼내면 기간이 빈 채로 나간다
        return ApiResponse.success(PublicEventResponse.from(
                event, closingSoon, publicEventService.publishedHtmlOf(event)));
    }
}
