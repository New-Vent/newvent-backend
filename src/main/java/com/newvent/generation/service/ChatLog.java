package com.newvent.generation.service;

import com.newvent.generation.domain.ChatMessageStatus;

// AI 채팅 내역을 남기는 자리
// 개인정보가 감지된 요청은 원문을 저장하지 않는다
public interface ChatLog {

    // 아무것도 남기지 않는다. 채팅 내역과 상관없는 테스트용
    ChatLog NOOP = new ChatLog() {
        @Override
        public Long request(Long eventId, String text, String targetBlock, boolean privacy) {
            return null;
        }

        @Override
        public void target(Long requestId, String targetBlock) {}

        @Override
        public void finish(Long requestId, Long eventId, ChatMessageStatus status, String reply) {}
    };

    // 관리자의 요청이 받아들여진 경우 admin 메시지를 PENDING 으로 남긴다.
    Long request(Long eventId, String text, String targetBlock, boolean privacy);

    // 라우터가 영역을 정한 뒤 target_block 을 채운다
    void target(Long requestId, String targetBlock);

    // 요청 메시지의 상태를 바꾸고, AI assistant를 같은 상태로 남긴다.
    void finish(Long requestId, Long eventId, ChatMessageStatus status, String reply);

    // ask_back(되묻기)은 실패가 아니때문에 COMPLETED로 남긴다
    default void finish(GenerationJob job, Long eventId, String doneReply) {
        Long requestId = job.chatRequestId();
        if (requestId == null) {
            return;
        }
        switch (job.phase()) {
            case DONE -> finish(requestId, eventId, ChatMessageStatus.COMPLETED, doneReply);
            case ASK_BACK -> finish(requestId, eventId, ChatMessageStatus.COMPLETED, job.message());
            case CANCELLED -> finish(requestId, eventId, ChatMessageStatus.CANCELLED, job.message());
            default -> finish(requestId, eventId, ChatMessageStatus.FAILED,
                    job.message() != null ? job.message() : "작업을 끝내지 못했습니다.");
        }
    }
}
