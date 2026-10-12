package com.newvent.generation.dto;

import java.time.OffsetDateTime;

import com.newvent.generation.domain.ChatMessage;
import com.newvent.generation.domain.ChatMessageStatus;
import com.newvent.generation.domain.ChatRole;

import io.swagger.v3.oas.annotations.media.Schema;

// 채팅 내역 한 줄
public record ChatMessageResponse(
        Long id,
        ChatRole role,
        @Schema(description = "개인정보 후보가 감지된 요청은 원문 대신 \"[개인정보 포함 요청]\" 이 나온다")
                String content,
        @Schema(description = "고른(또는 라우터가 정한) 영역 key. 여러 개면 쉼표로 이어진다. 없으면 null")
                String targetBlock,
        ChatMessageStatus status,
        OffsetDateTime createdAt,
        @Schema(description = "이 요청으로 만들어진 버전 번호. 버전을 만들지 않았으면(실패 · 되묻기 · AI 응답) null")
                Integer versionNo) {

    public static ChatMessageResponse of(ChatMessage message, Integer versionNo) {
        return new ChatMessageResponse(
                message.getId(), message.getRole(), message.getContent(), message.getTargetBlock(),
                message.getStatus(), message.getCreatedAt(), versionNo);
    }
}
