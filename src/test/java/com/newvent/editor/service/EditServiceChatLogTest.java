package com.newvent.editor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.editor.service.EditServiceTest.CountingGateway;
import com.newvent.editor.service.EditServiceTest.FakeRetry;
import com.newvent.editor.service.EditServiceTest.FakeRouter;
import com.newvent.filtering.FilteringPolicy;
import com.newvent.generation.domain.ChatMessageStatus;
import com.newvent.generation.service.EventGuard;
import com.newvent.generation.service.GenerationJob;
import com.newvent.generation.service.GenerationJobStore;
import com.newvent.generation.service.LlmCallRecorder;
import com.newvent.generation.service.RecordingChatLog;
import com.newvent.generation.service.RetryService;
import com.newvent.generation.service.VersionStore;

//채팅 수정이 채팅 내역을 어떻게 남기는지. DB 없이 RecordingChatLog 와 VersionStore.InMemory 로 본다.
// 실제 저장은 JpaChatLogTest · JpaVersionStoreTest 가 본다.
class EditServiceChatLogTest {

    private static final long EVENT = 1L;

    private static final String BASE = """
            <section data-block="hero"><h1>여름 데이터 대방출</h1>\
            <p>기간 <span data-slot="period"></span> 까지</p></section>
            <section data-block="benefits"><ul><li>데이터 10GB</li><li>쿠폰</li></ul></section>
            <section data-block="steps"><ol><li>앱 열기</li><li>버튼 누르기</li></ol></section>
            <section data-block="cta"><button class="btn">참여하기</button></section>""";

    private static final String NEW_HERO =
            "<section data-block=\"hero\"><h1>가을 대축제</h1>"
            + "<p>기간 <span data-slot=\"period\"></span> 까지</p></section>";

    private FakeRouter router;
    private FakeRetry retry;
    private GenerationJobStore jobs;
    private VersionStore.InMemory versions;
    private RecordingChatLog chat;
    private EditService service;

    @BeforeEach
    void setUp() {
        router = new FakeRouter();
        retry = new FakeRetry();
        jobs = new GenerationJobStore();
        versions = new VersionStore.InMemory();
        chat = new RecordingChatLog();
        service = new EditService(router, retry, versions, new EventGuard.Open(), jobs,
                LlmCallRecorder.none(), new CountingGateway(), new FilteringPolicy(), chat);
        versions.save(EVENT, BASE, null);   // 고칠 페이지. v1
    }

    private static GenerationJob started(EditService.StartResult r) {
        assertInstanceOf(EditService.StartResult.Started.class, r, "시작되지 않았습니다: " + r);
        return ((EditService.StartResult.Started) r).job();
    }

    // 작업이 끝나고 자리까지 빌 때까지 기다린다.
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

    private GenerationJob run(EditCommand cmd) {
        return await(started(service.start(cmd)));
    }

    @Test
    @DisplayName("라우터가 영역을 정한 수정이 성공하면 요청을 남기고, target_block 을 채우고, 만든 버전을 요청에 건다")
    void 수정_성공_라우터가_영역을_정한다() {
        router.willReturn(new RawRoute("EDIT", "hero", null));
        retry.willReturn(new RetryService.Result(true, NEW_HERO, List.of()));

        GenerationJob job = run(new EditCommand(EVENT, "여름 이벤트", "제목을 가을 축제로 바꿔줘"));

        assertEquals(GenerationJob.Phase.DONE, job.phase(), job.message());
        assertEquals(1, chat.requests.size());
        RecordingChatLog.Request request = chat.requests.get(0);
        assertEquals("제목을 가을 축제로 바꿔줘", request.text());
        assertNull(request.targetBlock(), "관리자가 영역을 안 골랐으니 시작할 때는 비어 있어야 합니다.");
        assertEquals(false, request.privacy());
        assertEquals("hero", chat.targets.get(request.id()), "라우터가 정한 영역이 채워져야 합니다.");
        assertEquals(request.id(), versions.requestMessageOf(job.versionId()));
        assertEquals(new RecordingChatLog.Finish(
                request.id(), EVENT, ChatMessageStatus.COMPLETED, "수정을 반영했습니다."), chat.finishes.get(0));
    }

    @Test
    @DisplayName("관리자가 영역을 골랐으면 시작할 때 target_block 에 같이 남는다")
    void 수정_성공_고른_영역() {
        router.willReturn(new RawRoute("EDIT", "hero", null));
        retry.willReturn(new RetryService.Result(true, NEW_HERO, List.of()));

        GenerationJob job = run(new EditCommand(EVENT, "여름 이벤트", "제목을 가을 축제로 바꿔줘", List.of("hero")));

        assertEquals(GenerationJob.Phase.DONE, job.phase(), job.message());
        assertEquals("hero", chat.requests.get(0).targetBlock());
        assertTrue(chat.targets.isEmpty(), "고른 영역이 있으면 라우터 결과로 덮어쓰지 않습니다.");
    }

    @Test
    @DisplayName("라우터가 요청을 못 알아들어 되묻기로 끝나면 COMPLETED 로 끝맺고 질문이 응답이다. 버전은 없다")
    void 라우터_되묻기() {
        router.willNotUnderstand();

        GenerationJob job = run(new EditCommand(EVENT, "여름 이벤트", "그거 좀 바꿔줘"));

        assertEquals(GenerationJob.Phase.ASK_BACK, job.phase());
        assertEquals(1, chat.requests.size());
        assertEquals(ChatMessageStatus.COMPLETED, chat.finishes.get(0).status());
        assertEquals(job.message(), chat.finishes.get(0).reply());
        assertEquals(1, versions.latest(EVENT).orElseThrow().versionNo(), "되묻기는 버전을 만들지 않습니다.");
    }

    @Test
    @DisplayName("수정이 실패하면 요청을 FAILED 로 끝맺고 실패 문구를 응답으로 남긴다")
    void 수정_실패() {
        router.willReturn(new RawRoute("EDIT", "hero", null));
        retry.willReturn(new RetryService.Result(false, null, List.of()));

        GenerationJob job = run(new EditCommand(EVENT, "여름 이벤트", "제목을 가을 축제로 바꿔줘"));

        assertEquals(GenerationJob.Phase.FAILED, job.phase());
        assertEquals(ChatMessageStatus.FAILED, chat.finishes.get(0).status());
        assertEquals(job.message(), chat.finishes.get(0).reply());
        assertNull(versions.requestMessageOf(versions.latest(EVENT).orElseThrow().versionId()));
    }

    @Test
    @DisplayName("개인정보가 감지된 요청은 확인 전 되묻기와 확인 후 재전송 모두 privacy 로 남고, 버전은 재전송에만 걸린다")
    void 개인정보_요청() {
        String request = "제목에 a@example.com 안내 문구 추가해줘";
        GenerationJob question = run(new EditCommand(EVENT, "여름 이벤트", request));
        assertEquals(GenerationJob.Phase.ASK_BACK, question.phase());
        assertEquals(1, chat.requests.size());
        assertTrue(chat.requests.get(0).privacy());
        assertEquals(ChatMessageStatus.COMPLETED, chat.finishes.get(0).status());

        router.willReturn(new RawRoute("EDIT", "hero", null));
        retry.willReturn(new RetryService.Result(true, NEW_HERO, List.of()));
        GenerationJob reply = run(new EditCommand(EVENT, "여름 이벤트", request, question.jobId(), true));

        assertEquals(GenerationJob.Phase.DONE, reply.phase(), reply.message());
        assertEquals(2, chat.requests.size());
        assertTrue(chat.requests.get(1).privacy());
        assertEquals(chat.requests.get(1).id(), versions.requestMessageOf(reply.versionId()));
        assertNull(question.versionId(), "되묻기는 버전을 만들지 않으므로 연결할 요청도 없습니다.");
    }

    @Test
    @DisplayName("거부된 요청(빈 요청문)은 채팅 내역에 남기지 않는다")
    void 거부된_요청은_남기지_않는다() {
        EditService.StartResult result = service.start(new EditCommand(EVENT, "여름 이벤트", ""));

        assertInstanceOf(EditService.StartResult.Rejected.class, result);
        assertTrue(chat.requests.isEmpty());
    }
}
