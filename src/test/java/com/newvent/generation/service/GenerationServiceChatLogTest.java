package com.newvent.generation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.filtering.FilteringPolicy;
import com.newvent.generation.domain.ChatMessageStatus;
import com.newvent.generation.service.GenerationServiceTest.FakeRetry;
import com.newvent.generation.service.GenerationServiceTest.FakeSimilarity;
import com.newvent.registry.BlockValidator;

// 생성이 채팅 내역을 어떻게 남기는지. DB 없이 RecordingChatLog 와 VersionStore.InMemory 로 본다.
// 실제 저장은 JpaChatLogTest · JpaVersionStoreTest 가 본다.
class GenerationServiceChatLogTest {

    private static final String GENERATED = """
            <section data-block="hero"><h1>여름 데이터 대방출</h1><p>걱정 없이</p></section>
            <section data-block="benefits"><ul><li>데이터 10GB</li><li>쿠폰</li></ul></section>
            <section data-block="cta"><a href="#" class="btn">참여하기</a></section>
            """;

    private FakeRetry retry;
    private GenerationJobStore jobs;
    private VersionStore.InMemory versions;
    private RecordingChatLog chat;
    private GenerationService service;

    @BeforeEach
    void setUp() {
        retry = new FakeRetry();
        jobs = new GenerationJobStore();
        versions = new VersionStore.InMemory();
        chat = new RecordingChatLog();
        service = serviceWith(chat);
    }

    private GenerationService serviceWith(ChatLog log) {
        return new GenerationService(
                retry,
                new TemplateService(new ResourceTemplateLoader()),
                versions, new EventGuard.Open(), jobs, LlmCallRecorder.none(),
                new FilteringPolicy(), new FakeSimilarity(), log);
    }

    private static RetryService.Result ok() {
        return new RetryService.Result(true, BlockValidator.sanitizeGenerated(GENERATED), List.of());
    }

    private GenerationJob await(GenerationJob job) {
        long deadline = System.currentTimeMillis() + 2000;
        while ((!job.done() || jobs.ofEvent(job.eventId()).isPresent())
                && System.currentTimeMillis() < deadline) {
            Thread.onSpinWait();
        }
        assertTrue(job.done(), "2초 안에 안 끝났습니다. 단계=" + job.phase());
        assertTrue(jobs.ofEvent(job.eventId()).isEmpty(), "끝났는데 자리를 비우지 않았습니다.");
        return job;
    }

    private static GenerationJob started(GenerationService.StartResult r) {
        assertInstanceOf(GenerationService.StartResult.Started.class, r, "시작되지 않았습니다: " + r);
        return ((GenerationService.StartResult.Started) r).job();
    }

    private static GenerateCommand blank(long id, String text) {
        return new GenerateCommand(id, null, false, "여름 이벤트", "2026.07.01 ~ 07.31", null, text);
    }

    @Test
    @DisplayName("AI 생성이 성공하면 요청을 남기고, 만든 버전을 그 요청에 건다")
    void 백지_생성_성공() {
        retry.willReturn(ok());

        GenerationJob job = await(started(service.start(blank(1L, "여름 수영장 이벤트 만들어줘"))));

        assertEquals(GenerationJob.Phase.DONE, job.phase(), job.message());
        assertEquals(1, chat.requests.size());
        RecordingChatLog.Request request = chat.requests.get(0);
        assertEquals("여름 수영장 이벤트 만들어줘", request.text());
        assertEquals(1L, request.eventId());
        assertEquals(false, request.privacy());
        assertEquals(request.id(), versions.requestMessageOf(job.versionId()),
                "새 버전이 그 요청 메시지를 가리켜야 합니다.");

        assertEquals(1, chat.finishes.size());
        assertEquals(new RecordingChatLog.Finish(
                request.id(), 1L, ChatMessageStatus.COMPLETED, "페이지를 만들었습니다."), chat.finishes.get(0));
    }

    @Test
    @DisplayName("템플릿으로 만들기는 대화가 아니라서 채팅 내역을 남기지 않는다")
    void 템플릿_생성은_남기지_않는다() {
        GenerationJob job = await(started(service.start(new GenerateCommand(
                1L, "holiday_gift", false, "한가위 이벤트", "2026.09.20 ~ 10.05", null, null))));

        assertEquals(GenerationJob.Phase.DONE, job.phase(), job.message());
        assertTrue(chat.requests.isEmpty());
        assertTrue(chat.finishes.isEmpty());
        assertNull(versions.requestMessageOf(job.versionId()));
    }

    @Test
    @DisplayName("생성이 실패하면 요청을 FAILED 로 끝맺고 실패 문구를 응답으로 남긴다. 버전은 없다")
    void 생성_실패() {
        retry.willReturn(new RetryService.Result(false, null, List.of()));

        GenerationJob job = await(started(service.start(blank(1L, "여름 수영장 이벤트"))));

        assertEquals(GenerationJob.Phase.FAILED, job.phase());
        assertEquals(1, chat.requests.size());
        assertEquals(ChatMessageStatus.FAILED, chat.finishes.get(0).status());
        assertEquals(job.message(), chat.finishes.get(0).reply());
        assertTrue(versions.latest(1L).isEmpty());
    }

    @Test
    @DisplayName("개인정보가 감지된 첫 요청은 원문 저장 대상이 아니다(privacy) - 되묻기는 COMPLETED 로 끝맺는다")
    void 개인정보_되묻기() {
        GenerationJob job = started(service.start(blank(1L, "문의는 a@example.com 으로 받아줘")));

        assertEquals(GenerationJob.Phase.ASK_BACK, job.phase());
        assertEquals(1, chat.requests.size());
        assertTrue(chat.requests.get(0).privacy(), "개인정보 후보가 든 요청은 privacy 로 남겨야 합니다.");
        assertEquals(ChatMessageStatus.COMPLETED, chat.finishes.get(0).status());
        assertEquals(job.message(), chat.finishes.get(0).reply());
        assertTrue(versions.latest(1L).isEmpty());
    }

    @Test
    @DisplayName("개인정보를 확인하고 재전송한 요청도 privacy 이고, 새 버전은 이 재전송 요청에만 걸린다")
    void 개인정보_확인_후_재전송() {
        String request = "문의는 a@example.com 으로 받아줘";
        GenerationJob question = started(service.start(blank(1L, request)));
        retry.willReturn(ok());

        GenerationJob reply = await(started(service.start(new GenerateCommand(
                1L, null, "여름 이벤트", null, null, request, question.jobId(), true))));

        assertEquals(GenerationJob.Phase.DONE, reply.phase(), reply.message());
        assertEquals(2, chat.requests.size());
        assertTrue(chat.requests.get(1).privacy());
        assertEquals(chat.requests.get(1).id(), versions.requestMessageOf(reply.versionId()));
        assertEquals(ChatMessageStatus.COMPLETED, chat.finishes.get(1).status());
    }

    @Test
    @DisplayName("거부된 요청(빈 요청문)은 채팅 내역에 남기지 않는다")
    void 거부된_요청은_남기지_않는다() {
        GenerationService.StartResult result = service.start(blank(1L, ""));

        assertInstanceOf(GenerationService.StartResult.Rejected.class, result);
        assertTrue(chat.requests.isEmpty());
    }

    @Test
    @DisplayName("채팅 내역을 남기지 못해도(null) 생성은 성공하고 버전은 연결 없이 저장된다")
    void 채팅_내역_실패해도_생성은_계속된다() {
        ChatLog broken = new ChatLog() {
            @Override
            public Long request(Long eventId, String text, String targetBlock, boolean privacy) {
                return null;
            }

            @Override
            public void target(Long requestId, String targetBlock) {}

            @Override
            public void finish(Long requestId, Long eventId, ChatMessageStatus status, String reply) {
                throw new AssertionError("요청을 못 남겼으면 끝맺을 것도 없습니다.");
            }
        };
        retry.willReturn(ok());

        GenerationJob job = await(started(serviceWith(broken).start(blank(1L, "여름 수영장 이벤트"))));

        assertEquals(GenerationJob.Phase.DONE, job.phase(), job.message());
        assertNull(versions.requestMessageOf(job.versionId()));
    }
}
