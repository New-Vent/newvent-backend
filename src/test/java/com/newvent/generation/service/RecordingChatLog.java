package com.newvent.generation.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import com.newvent.generation.domain.ChatMessageStatus;

// DB 를 띄우지 않는 서비스 테스트용 ChatLog. 호출을 그대로 적어 둔다.
public final class RecordingChatLog implements ChatLog {

    public record Request(Long id, Long eventId, String text, String targetBlock, boolean privacy) {}

    public record Finish(Long requestId, Long eventId, ChatMessageStatus status, String reply) {}

    public final List<Request> requests = new CopyOnWriteArrayList<>();
    public final List<Finish> finishes = new CopyOnWriteArrayList<>();
    public final Map<Long, String> targets = new ConcurrentHashMap<>();

    private final AtomicLong ids = new AtomicLong(100);

    @Override
    public Long request(Long eventId, String text, String targetBlock, boolean privacy) {
        Long id = ids.incrementAndGet();
        requests.add(new Request(id, eventId, text, targetBlock, privacy));
        return id;
    }

    @Override
    public void target(Long requestId, String targetBlock) {
        if (requestId != null) {
            targets.put(requestId, targetBlock);
        }
    }

    @Override
    public void finish(Long requestId, Long eventId, ChatMessageStatus status, String reply) {
        finishes.add(new Finish(requestId, eventId, status, reply));
    }
}
