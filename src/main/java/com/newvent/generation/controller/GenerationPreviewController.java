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


/**
 * 만들어진 페이지를 본다. **editor 가 준비될 때까지 쓰는 임시 엔드포인트다.**
 *
 */
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
