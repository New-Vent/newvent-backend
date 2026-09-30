package com.newvent.event.dto.response;

import com.newvent.generation.service.VersionStore;

/**
 * 에디터 직접 편집 응답 DTO.
 *
 * @param versionId 새로 저장된 버전의 고유 ID (이후 수정 요청의 기준)
 * @param versionNo 이벤트 내 버전 순번
 */
public record DirectEditResponse(Long versionId, int versionNo) {

    public static DirectEditResponse from(VersionStore.Saved saved) {
        return new DirectEditResponse(saved.versionId(), saved.versionNo());
    }
}
