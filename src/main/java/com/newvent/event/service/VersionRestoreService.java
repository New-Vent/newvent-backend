package com.newvent.event.service;

import java.util.Objects;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventVersion;
import com.newvent.event.dto.response.DirectEditResponse;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventRepository;
import com.newvent.event.repository.EventVersionRepository;
import com.newvent.generation.service.VersionStore;

/**
 * 저장 지점으로 되돌리기.
 */
@Service
public class VersionRestoreService {

    private final EventRepository eventRepository;
    private final EventVersionRepository eventVersionRepository;
    private final VersionStore versionStore;

    public VersionRestoreService(EventRepository eventRepository,
                                 EventVersionRepository eventVersionRepository,
                                 VersionStore versionStore) {
        this.eventRepository = Objects.requireNonNull(eventRepository, "eventRepository");
        this.eventVersionRepository = Objects.requireNonNull(eventVersionRepository, "eventVersionRepository");
        this.versionStore = Objects.requireNonNull(versionStore, "versionStore");
    }

    /**
     * 그 버전의 HTML 로 <b>새 버전</b>을 만든다.
     */
    @Transactional
    public DirectEditResponse restore(Long eventId, Long versionId, Long adminId) {
        Objects.requireNonNull(eventId, "eventId는 필수입니다.");
        Objects.requireNonNull(versionId, "versionId는 필수입니다.");

        // 1. 이벤트 존재 확인 (삭제된 이벤트는 제외) — DirectEditService 와 같은 방식
        Event event = eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND));
        if (adminId == null || event.getOwnerAdmin() == null
                || !adminId.equals(event.getOwnerAdmin().getId())) {
            throw new AccessDeniedException("이벤트 소유 관리자만 버전을 되돌릴 수 있습니다.");
        }

        // 2. 되돌릴 버전 — eventId 를 쿼리가 같이 본다.
        //    ★ 남의 이벤트 버전도 "없음" 으로 답한다. 403 으로 답하면 버전 id 를 훑어
        //      남의 이벤트가 몇 개인지 셀 수 있다.
        EventVersion source = eventVersionRepository
                .findByIdAndEventId(versionId, eventId)
                .orElseThrow(() -> new EventException(EventErrorCode.VERSION_NOT_FOUND));

        // 3. 그 내용으로 새 버전. source_version_id 가 되돌린 원본을 가리킨다.
        //    ★ versionStore.save 가 version_no 를 매기고 이벤트 행을 잠근다.
        VersionStore.Saved saved =
                versionStore.save(eventId, source.getHtmlContent(), versionId);

        return DirectEditResponse.from(saved);
    }
}
