package com.newvent.rag.dto.request;

import jakarta.validation.constraints.NotNull;

// 수동 재색인 요청. 한 버전만 돌린다. 이벤트 전체 버전은 POST /reindex/all 로 보낸다.
public record ReindexRequest(@NotNull Long eventId, @NotNull Long versionId) {

}
