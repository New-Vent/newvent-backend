package com.newvent.generation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.newvent.event.domain.Event;
import com.newvent.event.repository.EventRepository;
import com.newvent.generation.domain.ChatMessage;
import com.newvent.generation.domain.ChatMessageStatus;
import com.newvent.generation.domain.ChatRole;
import com.newvent.generation.repository.ChatMessageRepository;

// 채팅 내역을 chat_messages 에 남긴다.
// 호출마다 짧은 새 트랜잭션이다
// DB 오류는 삼킨다. 채팅 내역이 안 남았다고 페이지 생성이 실패하면 안 된다.
// 로그에는 eventId 만 남기고 content 는 절대 찍지 않는다 - 개인정보가 들어 있을 수 있기 때문에
@Service
public class JpaChatLog implements ChatLog {

    private static final Logger log = LoggerFactory.getLogger(JpaChatLog.class);

    // chat_messages.target_block 컬럼 길이
    private static final int TARGET_BLOCK_MAX = 50;

    private final ChatMessageRepository messages;
    private final EventRepository events;
    private final TransactionTemplate tx;

    public JpaChatLog(ChatMessageRepository messages, EventRepository events,
                      PlatformTransactionManager txManager) {
        this.messages = messages;
        this.events = events;
        this.tx = new TransactionTemplate(txManager);
        // 항상 새 트랜잭션. 호출한 쪽의 트랜잭션에 합류 x
        // 채팅 내역은 요청을 받은 사실의 기록이라 호출자의 커밋·롤백과 상관없이 남아야 한다.
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public Long request(Long eventId, String text, String targetBlock, boolean privacy) {
        try {
            return tx.execute(status -> {
                Event event = events.getReferenceById(eventId);
                String content = privacy ? ChatMessage.PRIVACY_PLACEHOLDER : text;
                return messages.save(ChatMessage.create(
                        event, ChatRole.ADMIN, content, clip(targetBlock), ChatMessageStatus.PENDING)).getId();
            });
        } catch (RuntimeException e) {
            log.warn("채팅 내역을 남기지 못했습니다 (event={})", eventId, e);
            return null;
        }
    }

    // ★ 못 읽어도 수정은 진행한다 — 맥락은 있으면 좋은 것이지 없으면 막을 일이 아니다
    @Override
    public String previousRequest(Long eventId, Long beforeRequestId) {
        if (eventId == null || beforeRequestId == null) {
            return null;
        }
        try {
            return tx.execute(status -> messages
                    .findTop3ByEventIdAndRoleAndIdLessThanOrderByIdDesc(eventId, ChatRole.ADMIN, beforeRequestId)
                    .stream()
                    .map(ChatMessage::getContent)
                    // 개인정보가 감지된 요청은 원문 대신 자리표시자가 저장돼 있다 — 맥락으로 못 쓴다
                    .filter(c -> c != null && !c.isBlank() && !ChatMessage.PRIVACY_PLACEHOLDER.equals(c))
                    .findFirst()
                    .orElse(null));
        } catch (RuntimeException e) {
            log.warn("직전 요청을 읽지 못했습니다 (event={})", eventId, e);
            return null;
        }
    }

    @Override
    public void target(Long requestId, String targetBlock) {
        if (requestId == null) {
            return;
        }
        try {
            tx.executeWithoutResult(status -> messages.findById(requestId)
                    .ifPresent(m -> m.changeTargetBlock(clip(targetBlock))));
        } catch (RuntimeException e) {
            log.warn("채팅 내역의 대상 영역을 남기지 못했습니다 (message={})", requestId, e);
        }
    }

    @Override
    public void finish(Long requestId, Long eventId, ChatMessageStatus status, String reply) {
        if (requestId == null) {
            return;
        }
        try {
            tx.executeWithoutResult(s -> {
                // eventId 를 같이 본다 - 다른 이벤트의 메시지를 건드리지 않는다
                var request = messages.findByIdAndEventId(requestId, eventId);
                if (request.isEmpty()) {
                    return;
                }
                request.get().changeStatus(status);
                if (reply != null && !reply.isBlank()) {
                    Event event = events.getReferenceById(eventId);
                    messages.save(ChatMessage.create(event, ChatRole.ASSISTANT, reply, null, status));
                }
            });
        } catch (RuntimeException e) {
            log.warn("채팅 내역을 끝맺지 못했습니다 (event={}, message={})", eventId, requestId, e);
        }
    }

    // 서버가 켜질 때 PENDING 으로 남은 메시지를 FAILED 로 바꾼다.
    @EventListener(ApplicationReadyEvent.class)
    public void failStalePending() {
        try {
            Integer changed = tx.execute(status ->
                    messages.updateStatus(ChatMessageStatus.PENDING, ChatMessageStatus.FAILED));
            if (changed != null && changed > 0) {
                log.warn("처리 중으로 남아 있던 채팅 메시지 {}건을 실패로 정리했습니다.", changed);
            }
        } catch (RuntimeException e) {
            log.warn("처리 중으로 남은 채팅 메시지를 정리하지 못했습니다.", e);
        }
    }

    private static String clip(String targetBlock) {
        if (targetBlock == null) {
            return null;
        }
        return targetBlock.length() <= TARGET_BLOCK_MAX ? targetBlock : targetBlock.substring(0, TARGET_BLOCK_MAX);
    }
}
