package com.newvent.editor.service;

import com.newvent.event.domain.Event;

/**
 * 수정 요청 하나.
 */
public record EditCommand(Long eventId, String title, String requestText, java.util.UUID clarificationJobId, boolean privacyConfirmed) {
    public EditCommand(Long eventId, String title, String requestText, java.util.UUID clarificationJobId) {
        this(eventId, title, requestText, clarificationJobId, false);
    }
    public EditCommand(Long eventId, String title, String requestText) {
        this(eventId, title, requestText, null);
    }

    public static EditCommand of(Event event, String requestText) {
        return new EditCommand(event.getId(), event.getTitle(), requestText);
    }
}
