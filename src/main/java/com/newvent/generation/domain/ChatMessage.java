package com.newvent.generation.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.*;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.newvent.event.domain.Event;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 채팅의 한 줄. 관리자의 요청(ADMIN)과 그에 대한 AI의 응답(ASSISTANT)이 시간순으로 쌓인다.
 *
 * ★ 개인정보가 감지된 요청은 원문을 저장하지 않는다 - content 에 {@link #PRIVACY_PLACEHOLDER} 만 남긴다.
 *   이 규칙은 만드는 쪽(JpaChatLog)이 지킨다. 여기서는 받은 content 를 그대로 둔다.
 */
@Getter
@Entity
@Table(name = "chat_messages")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    // 개인정보 후보가 감지된 요청 대신 저장하는 표시
    public static final String PRIVACY_PLACEHOLDER = "[개인정보 포함 요청]";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ChatRole role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "target_block", length = 50)
    private String targetBlock;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ChatMessageStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "event_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_chat_messages_event")
    )
    private Event event;

    public static ChatMessage create(Event event, ChatRole role, String content,
                                     String targetBlock, ChatMessageStatus status) {
        ChatMessage message = new ChatMessage();
        message.event = event;
        message.role = role;
        message.content = content;
        message.targetBlock = targetBlock;
        message.status = status;
        return message;
    }

    public void changeStatus(ChatMessageStatus status) {
        this.status = status;
    }

    public void changeTargetBlock(String targetBlock) {
        this.targetBlock = targetBlock;
    }
}
