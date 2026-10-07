package com.newvent.editor.service;

import java.util.List;

import com.newvent.event.domain.Event;

/**
 * 수정 요청 하나.
 *
 * @param blocks 관리자가 미리보기에서 고른 영역 key. 비었으면 라우터가 요청문으로 영역을 정한다.
 *               검사(있는 블록인가 · 고칠 수 있는가)는 {@link EditService#start} 가 한다
 */
public record EditCommand(Long eventId, String title, String requestText, List<String> blocks,
                          java.util.UUID privacyConfirmationJobId, boolean privacyConfirmed) {

    public EditCommand {
        // ★ 중복은 여기서 접는다 — ["hero","hero"] 를 두 번 고치지 않게. 순서는 보낸 대로 둔다
        blocks = blocks == null ? List.of()
                : blocks.stream().filter(b -> b != null && !b.isBlank()).map(String::strip).distinct().toList();
    }

    public EditCommand(Long eventId, String title, String requestText, java.util.UUID privacyConfirmationJobId, boolean privacyConfirmed) {
        this(eventId, title, requestText, List.of(), privacyConfirmationJobId, privacyConfirmed);
    }

    public EditCommand(Long eventId, String title, String requestText, java.util.UUID privacyConfirmationJobId) {
        this(eventId, title, requestText, privacyConfirmationJobId, false);
    }

    public EditCommand(Long eventId, String title, String requestText, List<String> blocks) {
        this(eventId, title, requestText, blocks, null, false);
    }

    /** 영역 없이 — 요청문만으로 고친다 */
    public EditCommand(Long eventId, String title, String requestText) {
        this(eventId, title, requestText, List.of());
    }

    public static EditCommand of(Event event, String requestText) {
        return of(event, requestText, List.of());
    }

    public static EditCommand of(Event event, String requestText, List<String> blocks) {
        return new EditCommand(event.getId(), event.getTitle(), requestText, blocks);
    }

    public boolean hasBlocks() {
        return !blocks.isEmpty();
    }
}
