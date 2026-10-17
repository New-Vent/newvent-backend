package com.newvent.event.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.event.domain.Event;
import com.newvent.event.dto.request.DirectEditRequest;
import com.newvent.event.dto.response.DirectEditResponse;
import com.newvent.event.exception.DirectEditErrorCode;
import com.newvent.event.exception.DirectEditException;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventRepository;
import com.newvent.generation.service.VersionStore;
import com.newvent.registry.Looks;
import com.newvent.registry.PageShell;

/**
 * 에디터 직접 편집 서비스.
 * <p>
 * 관리자가 요청한 텍스트 · 버튼 스타일 · 블록 순서 · 배치 · 모양 수정을 기준 버전에 반영하고
 * {@code event_versions}에 새로운 버전으로 저장한다.
 */
@Service
public class DirectEditService {

    private final EventRepository eventRepository;
    private final VersionStore versionStore;
    private final Clock clock;

    public DirectEditService(EventRepository eventRepository, VersionStore versionStore, Clock clock) {
        this.eventRepository = Objects.requireNonNull(eventRepository, "eventRepository");
        this.versionStore = Objects.requireNonNull(versionStore, "versionStore");
        this.clock = Objects.requireNonNull(clock, "clock");
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

        // 메타데이터 수정과 같은 종료 정책. 기준 HTML 조회·편집·저장 전에 차단한다.
        if (event.editLocked(OffsetDateTime.now(clock))) {
            throw new EventException(EventErrorCode.EVENT_ENDED_NOT_EDITABLE);
        }

        // 2. 기준 버전 HTML 조회 (해당 이벤트의 버전인지 함께 검증)
        String baseHtml = versionStore.htmlOf(eventId, request.sourceVersionId())
                .orElseThrow(() -> new EventException(EventErrorCode.VERSION_NOT_FOUND));

        // 3. 순수 함수 적용기로 변경된 HTML 생성 (불일치 시 409, 인덱스/스타일 오류 시 400 발생)
        String updatedHtml = DirectEditor.apply(
                baseHtml, request.edits(), request.buttonStyle(), request.textStyle());

        // 삭제는 문구 인덱스 편집 뒤, 블록 순서 적용 전에 수행한다.
        updatedHtml = DirectEditor.applyDeletions(updatedHtml, request.removeBlocks(), request.removeItems());

        // 3-2. 블록 순서. ★ 문구·버튼을 고친 **뒤**다 —
        //      TextEdit 은 문서 전체를 훑은 텍스트 노드 번호로 자리를 가리키므로,
        //      순서를 먼저 바꾸면 그 번호가 전부 밀려 엉뚱한 문구가 바뀐다.
        //      거르는 규칙(중복 · 고정 블록)은 PageShell.applyOrder 가 갖는다.
        if (!request.blocks().isEmpty()) {
            updatedHtml = PageShell.applyOrder(updatedHtml, request.blocks());
        }

        // 3-3. 배치 · 모양. 순서 **뒤**다 — 순서를 세운 조각에 건다.
        //      못 붙이는 이유(영역 수 · 항목 수 · 템플릿 페이지)는 관리자에게 그대로 돌려준다
        if (request.hasLooks()) {
            String before = PageShell.settle(updatedHtml);
            try {
                updatedHtml = Looks.apply(updatedHtml, request.layoutPick(), request.variantPicks());
            } catch (Looks.Blocked e) {
                throw new DirectEditException(DirectEditErrorCode.LOOK_NOT_AVAILABLE, e.getMessage());
            }
            // ★ 이미 그 배치 · 모양이면 저장하지 않는다 — 똑같은 버전이 하나 더 쌓일 뿐이다
            // ★ 문구 · 버튼 · 순서를 같이 고쳤으면 저장한다. 모양이 그대로라는 이유로 막으면
            //   같이 보낸 문구 수정까지 버려진다
            if (updatedHtml.equals(before) && !request.hasOtherEdits()) {
                throw new DirectEditException(DirectEditErrorCode.LOOK_NOT_AVAILABLE, "이미 그 배치 · 모양입니다.");
            }
        }

        // 4. 새 버전으로 저장 (source_version_id 지정)
        VersionStore.Saved saved = versionStore.save(eventId, updatedHtml, request.sourceVersionId());

        return DirectEditResponse.from(saved);
    }
}
