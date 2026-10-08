package com.newvent.event.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.newvent.event.exception.DirectEditErrorCode;
import com.newvent.event.exception.DirectEditException;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 에디터 직접 편집 요청 DTO.
 *
 * @param sourceVersionId 기준이 되는 버전의 ID (event_versions.id)
 * @param edits           수정할 문구 목록
 * @param buttonStyle     수정할 CTA 버튼 스타일 (색상, 크기, 모양)
 */
public record DirectEditRequest(
        @Schema(description = "지금 에디터에 띄운 버전. 새 버전은 이 버전을 이어서 만든다.")
        @NotNull(message = "기준 버전 ID(sourceVersionId)는 필수입니다.")
        Long sourceVersionId,
        @Schema(description = "고칠 문구 목록. buttonStyle 과 둘 중 하나는 있어야 한다.")
        List<@Valid TextEdit> edits,
        @Valid ButtonStyle buttonStyle) {

    public DirectEditRequest {
        if ((edits == null || edits.isEmpty()) && buttonStyle == null) {
            throw new DirectEditException(DirectEditErrorCode.EMPTY_EDIT_REQUEST);
        }
    }
}
