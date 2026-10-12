package com.newvent.generation.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.newvent.auth.dto.AuthUser;
import com.newvent.common.response.ApiResponse;
import com.newvent.event.domain.Event;
import com.newvent.event.service.EventOwnerCheck;
import com.newvent.generation.dto.PreviewResponse;
import com.newvent.generation.exception.GenerationErrorCode;
import com.newvent.generation.exception.GenerationException;
import com.newvent.generation.service.GenerateCommand;
import com.newvent.generation.service.GenerationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 만들어진 페이지를 본다. **editor 가 준비될 때까지 쓰는 임시 엔드포인트다.**
 *
 */
@Tag(name = "생성 페이지 미리보기", description = "에디터가 준비될 때까지 쓰는 임시 API. "
        + "이벤트를 만든 관리자만 볼 수 있다 (아니면 403 COMMON403-0)")
@RestController
@RequestMapping("/api/admin/events/{eventId}/preview")
public class GenerationPreviewController {

    private final GenerationService generation;
    private final EventOwnerCheck ownerCheck;

    public GenerationPreviewController(GenerationService generation, EventOwnerCheck ownerCheck) {
        this.generation = generation;
        this.ownerCheck = ownerCheck;
    }

    /**
     * 저장된 마지막 버전에 이벤트 값을 채워 돌려준다.
     *
     * ★ 응답에 versionId 가 같이 나간다. 프론트가 이후 수정의 기준으로 쓴다.
     * ★ 이벤트 소유 관리자만 볼 수 있다.
     */
    @Operation(
            summary = "마지막 버전 미리보기",
            description = "가장 최근 버전에 이벤트 제목·기간 등을 채운 HTML 을 준다. 함께 오는 versionId 를 이후 수정의 기준으로 쓴다. "
                    + "이벤트가 없으면 404 (EVENT404-0), 아직 만든 페이지가 없으면 404 (GEN404-1)")
    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<PreviewResponse> preview(@PathVariable Long eventId,
                                                @AuthenticationPrincipal AuthUser admin) {

        Event event = ownerCheck.requireOwned(eventId, admin);
        return generation.render(GenerateCommand.forRender(event))
                .map(rendered -> ApiResponse.success(PreviewResponse.of(rendered)))
                .orElseThrow(() -> new GenerationException(GenerationErrorCode.PAGE_NOT_FOUND));
    }
}
