package com.newvent.editor.service;

import com.newvent.event.domain.Event;

/**
 * 수정 요청 하나.
 */
public record EditCommand(Long eventId, String title, String requestText) {

    public static EditCommand of(Event event, String requestText) {
        return new EditCommand(event.getId(), event.getTitle(), requestText);
    }
}
