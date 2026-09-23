package com.newvent.generation.service;

import java.util.Optional;

import com.newvent.common.exception.code.ErrorCode;

/**
 * "이 이벤트를 지금 건드려도 되는가" — 생성·수정 진입부에서 한 번 묻는다.
 */
public interface EventGuard {

    /** 막을 이유가 있으면 그 코드, 없으면 빈 값 */
    Optional<ErrorCode> rejectReason(Long eventId);

    /**
     * 아무것도 막지 않는 구현. **이제 빈이 아니다 — 테스트 전용이다.**
     */
    class Open implements EventGuard {
        @Override
        public Optional<ErrorCode> rejectReason(Long eventId) {
            return Optional.empty();
        }
    }
}
