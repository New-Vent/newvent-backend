package com.newvent.event.dto.request;

import jakarta.validation.constraints.NotNull;

// 이벤트 게시·재게시 요청, 게시할 버전을 명시적으로 선택

public record EventPublishRequest(
        @NotNull(message = "게시할 버전을 선택해주세요.")
        Long versionId
) {
}
