package com.newvent.generation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.generation.domain.ChatMessageStatus;

// 작업이 끝난 모습에 따라 채팅 내역을 어떻게 끝맺는지 확인
class ChatLogTest {

    private final RecordingChatLog chat = new RecordingChatLog();

    private static GenerationJob jobWithRequest(Long requestId) {
        GenerationJob job = new GenerationJob(1L);
        job.chatRequestId(requestId);
        return job;
    }

    @Test
    @DisplayName("성공하면 COMPLETED 로 끝맺고 성공 문장을 응답으로 남긴다")
    void 성공() {
        GenerationJob job = jobWithRequest(7L);
        job.to(GenerationJob.Phase.DONE);

        chat.finish(job, 1L, "만들었습니다.");

        assertEquals(1, chat.finishes.size());
        assertEquals(new RecordingChatLog.Finish(7L, 1L, ChatMessageStatus.COMPLETED, "만들었습니다."),
                chat.finishes.get(0));
    }

    @Test
    @DisplayName("되묻기는 실패가 아니다 - COMPLETED 로 끝맺고 질문이 응답이다")
    void 되묻기() {
        GenerationJob job = jobWithRequest(7L);
        job.askBack("어느 부분을 바꿀까요?");

        chat.finish(job, 1L, "무시되는 문장");

        assertEquals(ChatMessageStatus.COMPLETED, chat.finishes.get(0).status());
        assertEquals("어느 부분을 바꿀까요?", chat.finishes.get(0).reply());
    }

    @Test
    @DisplayName("실패하면 FAILED 로 끝맺고 관리자에게 보여준 실패 문구를 응답으로 남긴다")
    void 실패() {
        GenerationJob job = jobWithRequest(7L);
        job.fail("연결하지 못했습니다.");

        chat.finish(job, 1L, "무시되는 문장");

        assertEquals(ChatMessageStatus.FAILED, chat.finishes.get(0).status());
        assertEquals("연결하지 못했습니다.", chat.finishes.get(0).reply());
    }

    @Test
    @DisplayName("중단하면 CANCELLED 로 끝맺는다")
    void 중단() {
        GenerationJob job = jobWithRequest(7L);
        job.requestCancel();
        assertTrue(job.checkCancelled());

        chat.finish(job, 1L, "무시되는 문장");

        assertEquals(ChatMessageStatus.CANCELLED, chat.finishes.get(0).status());
        assertEquals(job.message(), chat.finishes.get(0).reply());
    }

    @Test
    @DisplayName("끝나지 않은 채로 불리면 FAILED 로 정리하고 기본 문구를 남긴다")
    void 끝나지_않은_작업() {
        GenerationJob job = jobWithRequest(7L);

        chat.finish(job, 1L, "무시되는 문장");

        assertEquals(ChatMessageStatus.FAILED, chat.finishes.get(0).status());
        assertEquals("작업을 끝내지 못했습니다.", chat.finishes.get(0).reply());
    }

    @Test
    @DisplayName("채팅 요청을 남기지 않은 작업(템플릿 · 남기기 실패)은 아무것도 하지 않는다")
    void 요청_id가_없으면_건너뛴다() {
        GenerationJob job = jobWithRequest(null);
        job.to(GenerationJob.Phase.DONE);

        chat.finish(job, 1L, "만들었습니다.");

        assertTrue(chat.finishes.isEmpty());
    }
}
