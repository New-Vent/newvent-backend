package com.newvent.event.service;

import java.util.Objects;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.event.domain.Event;
import com.newvent.event.dto.request.DirectEditRequest;
import com.newvent.event.dto.response.DirectEditResponse;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventRepository;
import com.newvent.generation.service.VersionStore;

/**
 * 에디터 직접 편집 서비스.
 * <p>
 * 관리자가 요청한 텍스트 및 버튼 스타일 수정을 기준 버전에 반영하고
 * {@code event_versions}에 새로운 버전으로 저장한다.
 */
@Service
public class DirectEditService {

    private final EventRepository eventRepository;
    private final VersionStore versionStore;

    public DirectEditService(EventRepository eventRepository, VersionStore versionStore) {
        this.eventRepository = Objects.requireNonNull(eventRepository, "eventRepository");
        this.versionStore = Objects.requireNonNull(versionStore, "versionStore");
    }

    /**
     * 직접 편집 요청을 적용하여 새 버전을 저장한다.
     *
     * @param eventId 이벤트 ID
     * @param request 직접 편집 요청 DTO
     * @return 새로 생성된 버전 ID 및 순번 응답
     */
    @Transactional
    public DirectEditResponse directEdit(Long eventId, DirectEditRequest request, Long adminId) {
        Objects.requireNonNull(eventId, "eventId는 필수입니다.");
        Objects.requireNonNull(request, "request는 필수입니다.");

        // 1. 이벤트 존재 확인 (삭제된 이벤트는 제외)
        Event event = eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND));
        if (adminId == null || event.getOwnerAdmin() == null
                || !adminId.equals(event.getOwnerAdmin().getId())) {
            throw new AccessDeniedException("이벤트 소유 관리자만 직접 편집할 수 있습니다.");
        }

        // 2. 기준 버전 HTML 조회 (해당 이벤트의 버전인지 함께 검증)
        String baseHtml = versionStore.htmlOf(eventId, request.sourceVersionId())
                .orElseThrow(() -> new EventException(EventErrorCode.VERSION_NOT_FOUND));

        // 3. 순수 함수 적용기로 변경된 HTML 생성 (불일치 시 409, 인덱스/스타일 오류 시 400 발생)
        String updatedHtml = DirectEditor.apply(baseHtml, request.edits(), request.buttonStyle());

        // 4. 새 버전으로 저장 (source_version_id 지정)
        VersionStore.Saved saved = versionStore.save(eventId, updatedHtml, request.sourceVersionId());

        return DirectEditResponse.from(saved);
    }
}
