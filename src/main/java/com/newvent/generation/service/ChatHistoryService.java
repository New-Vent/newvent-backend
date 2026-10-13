package com.newvent.generation.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.common.response.PageResponse;
import com.newvent.event.domain.Event;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventRepository;
import com.newvent.event.repository.EventVersionRepository;
import com.newvent.generation.domain.ChatMessage;
import com.newvent.generation.dto.ChatMessageResponse;
import com.newvent.generation.repository.ChatMessageRepository;

// 이벤트의 AI 채팅 내역을 읽기 (대화 이력 다시 보기). 이벤트를 만든 관리자만 본다
// 오래된 것부터 시간순으로하고 정렬은 쿼리가 정한다
@Service
public class ChatHistoryService {

    private final EventRepository events;
    private final ChatMessageRepository messages;
    private final EventVersionRepository versions;

    public ChatHistoryService(EventRepository events, ChatMessageRepository messages,
                              EventVersionRepository versions) {
        this.events = events;
        this.messages = messages;
        this.versions = versions;
    }

    @Transactional(readOnly = true)
    public PageResponse<ChatMessageResponse> list(Long eventId, Long adminId, int page, int size) {
        findOwnedEvent(eventId, adminId);

        Page<ChatMessage> found = messages.findPageByEventId(eventId, PageRequest.of(page, size));
        Map<Long, Integer> versionNos = versionNosOf(found.getContent());

        List<ChatMessageResponse> content = found.getContent().stream()
                .map(m -> ChatMessageResponse.of(m, versionNos.get(m.getId())))
                .toList();
        return PageResponse.of(content, page, size, found.getTotalElements());
    }

    // 이 페이지의 메시지가 만든 버전 번호를 쿼리 한 번으로 모은다
    private Map<Long, Integer> versionNosOf(List<ChatMessage> page) {
        Map<Long, Integer> result = new HashMap<>();
        if (page.isEmpty()) {
            return result;
        }
        for (Object[] row : versions.findVersionNosByRequestMessageIds(
                page.stream().map(ChatMessage::getId).toList())) {
            result.put((Long) row[0], (Integer) row[1]);
        }
        return result;
    }

    private Event findOwnedEvent(Long eventId, Long adminId) {
        Event event = events.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND));
        if (adminId == null
                || event.getOwnerAdmin() == null
                || !Objects.equals(event.getOwnerAdmin().getId(), adminId)) {
            throw new EventException(EventErrorCode.EVENT_NOT_ACCESSIBLE);
        }
        return event;
    }
}
