package com.newvent.generation.dto;

import com.newvent.generation.service.GenerationService;

/**
 *
 * ★ 슬롯은 채워진 상태다
 *   저장본에는 기간·참여링크가 비어 있고, 보여줄 때 Slots.fill() 이 채운다.
 *
 * ★ 어느 버전인지 같이 준다
 *   폴링을 놓쳐도 화면을 새로 열면 이쪽으로 복구된다.
 */
public record PreviewResponse(String html, Long versionId, Integer versionNo) {

    public static PreviewResponse of(GenerationService.Rendered rendered) {
        return new PreviewResponse(
                rendered.html(), rendered.versionId(), rendered.versionNo());
    }
}
