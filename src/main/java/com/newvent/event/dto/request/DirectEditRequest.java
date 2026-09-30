package com.newvent.event.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.newvent.event.exception.DirectEditErrorCode;
import com.newvent.event.exception.DirectEditException;

/**
 * 에디터 직접 편집 요청 DTO.
 *
 * @param sourceVersionId 기준이 되는 버전의 ID (event_versions.id)
 * @param edits           수정할 문구 목록
 * @param buttonStyle     수정할 CTA 버튼 스타일 (색상, 크기, 모양)
 */
public record DirectEditRequest(
        @NotNull(message = "기준 버전 ID(sourceVersionId)는 필수입니다.")
        Long sourceVersionId,
        List<@Valid TextEdit> edits,
        @Valid ButtonStyle buttonStyle) {

    public DirectEditRequest {
        if ((edits == null || edits.isEmpty()) && buttonStyle == null) {
            throw new DirectEditException(DirectEditErrorCode.EMPTY_EDIT_REQUEST);
        }
    }
}
