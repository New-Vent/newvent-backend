package com.newvent.rag.dto.request;

import jakarta.validation.constraints.NotNull;

// 수동 재색인 요청. versionId 가 없으면 이벤트 전체 버전을 돌린다.
public record ReindexRequest(@NotNull Long eventId, Long versionId) {

}
