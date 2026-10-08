package com.newvent.event.service;

import com.newvent.event.domain.Event;
import com.newvent.generation.service.PeriodText;
import com.newvent.registry.PageShell;
import com.newvent.registry.Slots;

// 이벤트 목록 썸네일용 HTML 조각. 공개 목록과 관리자 목록이 같은 규칙을 쓰도록 한 곳에 둔다
final class ThumbnailHtml {

    private ThumbnailHtml() {}

    // 버전 HTML 이 없거나 hero 블록이 없으면 null
    static String of(Event event, String versionHtml) {
        if (versionHtml == null || versionHtml.isBlank()) {
            return null;
        }
        String filled = Slots.fill(
                versionHtml,
                PeriodText.of(event.getStartDate(), event.getEndDate()),
                null);
        return PageShell.heroOnly(filled);
    }
}
