package com.newvent.event.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "이벤트 상태. DRAFT 임시저장, PUBLISHED 게시중, ENDED 게시종료 (관리자가 종료하거나 종료 시각이 지나면 서버가 바꾼다. 되돌릴 수 없다)")
public enum EventStatus {
    DRAFT,
    PUBLISHED,
    ENDED
}
